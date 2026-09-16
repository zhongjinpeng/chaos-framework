package com.michael.chaos.mybatis.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.michael.chaos.mybatis.sql.SqlLiterals;
import com.michael.chaos.mybatis.tenant.ChaosMybatisProperties;
import com.michael.chaos.security.api.datascope.DataScopeContext;
import com.michael.chaos.security.api.datascope.DataScopeRequest;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

/**
 * 基于 MyBatis Plus 官方数据权限扩展点的 SQL 条件处理器。
 *
 * <p>字段名和别名按标识符白名单校验；条件值通过 {@link SqlLiterals} 按数据库方言转义后再生成字面量。
 * JSqlParser 的 StringValue 不做转义，值里带单引号（或 MySQL 下带反斜杠）时会改写 SQL。</p>
 */
public class ChaosDataPermissionHandler implements MultiDataPermissionHandler {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final DataScopeProvider dataScopeProvider;

    private final ChaosMybatisProperties properties;

    /**
     * 创建数据权限处理器。
     */
    public ChaosDataPermissionHandler(DataScopeProvider dataScopeProvider, ChaosMybatisProperties properties) {
        this.dataScopeProvider = dataScopeProvider;
        this.properties = properties;
    }

    /**
     * 为指定表构造数据权限条件。
     */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (!properties.getDataScope().isEnabled() || ignoreTable(table.getName())) {
            return where;
        }
        Optional<DataScopeRequest> currentRequest = DataScopeContext.currentRequest();
        if (currentRequest.isEmpty() || currentRequest.get().scope().isBlank()) {
            return where;
        }
        DataScopeRequest request = currentRequest.get().withSql(mappedStatementId, table.getName());
        Optional<DataScopeCondition> optionalCondition = dataScopeProvider.condition(request);
        if (optionalCondition.isEmpty() || optionalCondition.get().isEmpty()) {
            return emptyCondition(where);
        }
        Expression dataScopeExpression = buildExpression(table, optionalCondition.get());
        return where == null ? dataScopeExpression : new AndExpression(where, dataScopeExpression);
    }

    /**
     * 构造数据权限表达式。
     */
    private Expression buildExpression(Table table, DataScopeCondition condition) {
        List<Expression> expressions = condition.rules().stream()
                .filter(rule -> !rule.isEmpty())
                .map(rule -> buildRuleExpression(table, rule))
                .toList();
        if (expressions.isEmpty()) {
            return emptyCondition(null);
        }
        Expression expression = expressions.getFirst();
        for (int i = 1; i < expressions.size(); i++) {
            expression = new AndExpression(expression, expressions.get(i));
        }
        return expression;
    }

    /**
     * 构造单条数据权限规则表达式。
     */
    private Expression buildRuleExpression(Table table, DataScopeCondition.Rule rule) {
        Column column = new Column(resolveTable(table, rule), safeIdentifier(rule.column()));
        return switch (rule.operator()) {
            case EQ -> new EqualsTo(column, literal(rule.values().getFirst()));
            case IN -> new InExpression(column, new ParenthesedExpressionList<>(new ExpressionList<>(stringValues(rule.values()))));
            case LIKE -> {
                LikeExpression expression = new LikeExpression();
                expression.setLeftExpression(column);
                expression.setRightExpression(literal(rule.values().getFirst()));
                yield expression;
            }
            case BETWEEN -> {
                Between expression = new Between();
                expression.setLeftExpression(column);
                expression.setBetweenExpressionStart(literal(rule.values().get(0)));
                expression.setBetweenExpressionEnd(literal(rule.values().get(1)));
                yield expression;
            }
        };
    }

    /**
     * 解析字段所属表，优先使用规则别名，其次使用 SQL 中的表别名。
     */
    private Table resolveTable(Table table, DataScopeCondition.Rule rule) {
        String alias = rule.tableAlias();
        if (alias == null || alias.isBlank()) {
            return table;
        }
        return new Table(safeIdentifier(alias));
    }

    /**
     * 把字符串值转换为已转义的 JSQLParser 字面量。
     */
    private List<Expression> stringValues(List<String> values) {
        return values.stream().map(this::literal).toList();
    }

    /**
     * 按方言转义单个值。
     */
    private Expression literal(String value) {
        return SqlLiterals.stringValue(value, properties.getDbType());
    }

    /**
     * 校验字段名和别名，避免权限服务配置变成 SQL 注入入口。
     */
    private String safeIdentifier(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException("数据权限字段或别名非法: " + identifier);
        }
        return identifier;
    }

    /**
     * 判断表是否忽略数据权限改写。
     */
    private boolean ignoreTable(String tableName) {
        return properties.getDataScope().getIgnoreTables().contains(tableName)
                || properties.getDataScope().getIgnoreTablePrefixes().stream().anyMatch(tableName::startsWith);
    }

    /**
     * 数据权限条件为空时按配置选择忽略或拒绝。
     */
    private Expression emptyCondition(Expression where) {
        if (properties.getDataScope().getEmptyConditionBehavior()
                == ChaosMybatisProperties.EmptyConditionBehavior.IGNORE) {
            return where;
        }
        EqualsTo denyExpression = new EqualsTo(new LongValue(1), new LongValue(0));
        return where == null ? denyExpression : new AndExpression(where, denyExpression);
    }
}

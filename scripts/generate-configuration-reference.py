#!/usr/bin/env python3
"""根据编译产物中的 Spring Boot 配置元数据生成配置参考文档。

为什么从元数据生成而不是手写：
    配置项的名称、类型、默认值和说明都来自 @ConfigurationProperties 源码（Javadoc）与
    additional-spring-configuration-metadata.json，由 spring-boot-configuration-processor 在编译期产出。
    手写的配置表会随着代码演进逐渐失真，生成的参考文档与代码始终一致。

用法：
    ./mvnw -B -DskipTests compile                                  # 先编译，产出 target/classes/META-INF/spring-configuration-metadata.json
    python3 scripts/generate-configuration-reference.py            # 重新生成 docs/configuration-reference.md
    python3 scripts/generate-configuration-reference.py --check    # 只校验已提交的文档是否最新（CI 使用），过期时返回 1

只依赖 Python 3 标准库。
"""

import argparse
import difflib
import json
import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "chaos-docs/src/main/resources/docs/configuration-reference.md"
METADATA = "target/classes/META-INF/spring-configuration-metadata.json"
ADDITIONAL_METADATA = "src/main/resources/META-INF/additional-spring-configuration-metadata.json"
EXCLUDED_PARTS = {"target", "chaos-examples", "chaos-architecture-tests", "chaos-relocations", "node_modules", ".git"}
POM_NAMESPACE = "{http://maven.apache.org/POM/4.0.0}"


def module_directories():
    """返回声明了配置属性的模块目录：源码中有 @ConfigurationProperties，或提供了 additional 元数据。"""
    modules = set()
    for java in ROOT.rglob("*.java"):
        relative = java.relative_to(ROOT)
        if EXCLUDED_PARTS.intersection(relative.parts) or "/src/main/java/" not in "/" + relative.as_posix():
            continue
        if "@ConfigurationProperties(" in java.read_text(encoding="utf-8"):
            modules.add(module_root(java))
    for additional in ROOT.rglob(ADDITIONAL_METADATA.split("/")[-1]):
        relative = additional.relative_to(ROOT)
        if EXCLUDED_PARTS.intersection(relative.parts) or "src/main/resources/META-INF" not in relative.as_posix():
            continue
        modules.add(module_root(additional))
    return sorted(modules)


def module_root(path):
    """向上查找最近的 pom.xml 所在目录。"""
    for parent in path.parents:
        if (parent / "pom.xml").is_file():
            return parent
    raise SystemExit(f"找不到 {path} 所属的 Maven 模块")


def artifact_id(module):
    project = ElementTree.parse(module / "pom.xml").getroot()
    element = project.find(f"{POM_NAMESPACE}artifactId")
    return element.text.strip()


def simple_type(type_name):
    """把 java.util.List<java.lang.String> 这类类型简化为 List<String>，内部类 $ 改为 .。"""
    if not type_name:
        return ""
    simplified = re.sub(r"\b(?:[a-z][\w]*\.)+([A-Z][\w$]*)", r"\1", type_name)
    # 枚举等内部类型只保留最后一段（ChaosGatewayProperties$TokenType → TokenType），可选值见“可选值”列。
    return re.sub(r"\b[A-Z]\w*\$(\w+)", r"\1", simplified)


def format_default(value):
    if value is None:
        return ""
    if isinstance(value, bool):
        return "`true`" if value else "`false`"
    if isinstance(value, list):
        return "`" + ", ".join(str(item) for item in value) + "`" if value else "`[]`"
    if value == "":
        return '`""`'
    return f"`{value}`"


def cell(text):
    """Markdown 表格单元格转义：竖线转义，换行和多余空白合并为单个空格。"""
    return re.sub(r"\s+", " ", str(text)).replace("|", "\\|").strip()


def plain_description(text):
    """把 Javadoc 片段转成适合表格的文本：{@code x} → `x`，{@link A#b} → `A#b`，去掉 <p> 等 HTML 标签。"""
    text = re.sub(r"\{@(?:code|literal)\s+([^}]*)\}", r"`\1`", text or "")
    text = re.sub(r"\{@link(?:plain)?\s+([^}\s]*)(?:\s+([^}]*))?\}", lambda m: f"`{m.group(2) or m.group(1)}`", text)
    return re.sub(r"</?[a-zA-Z][^>]*>", " ", text)


def load_properties(modules):
    properties = {}
    hints = {}
    missing = []
    for module in modules:
        metadata_file = module / METADATA
        if not metadata_file.is_file():
            missing.append(module.relative_to(ROOT).as_posix())
            continue
        metadata = json.loads(metadata_file.read_text(encoding="utf-8"))
        owner = artifact_id(module)
        for prop in metadata.get("properties", []):
            if not prop["name"].startswith("chaos."):
                continue
            properties[prop["name"]] = {**prop, "artifact": owner}
        for hint in metadata.get("hints", []):
            hints[hint["name"]] = hint
    if missing:
        raise SystemExit(
            "以下模块缺少编译期配置元数据，请先执行 ./mvnw -B -DskipTests compile：\n  " + "\n  ".join(missing))
    return properties, hints


def section_of(name):
    """按 chaos.<领域> 分节，例如 chaos.storage.minio.endpoint 归入 chaos.storage。"""
    parts = name.split(".")
    return ".".join(parts[:2])


def render(properties, hints):
    lines = [
        "# Configuration Reference",
        "",
        "<!-- 本文件由 scripts/generate-configuration-reference.py 根据编译期配置元数据生成，请勿手工修改。 -->",
        "",
        "全部 `chaos.*` 配置项的完整参考，由 `spring-configuration-metadata.json` 自动生成，与代码始终一致。",
        "按场景挑选常用配置请先看 [Configuration Index](configuration-index.md) 和 [配置模板](templates/README.md)。",
        "",
        "重新生成：",
        "",
        "```bash",
        "./mvnw -B -DskipTests compile",
        "python3 scripts/generate-configuration-reference.py",
        "```",
        "",
    ]
    sections = {}
    for name in sorted(properties):
        sections.setdefault(section_of(name), []).append(properties[name])
    lines.append("## 目录")
    lines.append("")
    for section in sections:
        anchor = section.replace(".", "")
        lines.append(f"- [`{section}`](#{anchor})（{len(sections[section])} 项）")
    lines.append("")
    for section, items in sections.items():
        lines.append(f"## {section}")
        lines.append("")
        lines.append("| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |")
        lines.append("| --- | --- | --- | --- | --- | --- |")
        for prop in items:
            description = plain_description(prop.get("description", ""))
            deprecation = prop.get("deprecation")
            if deprecation is not None:
                reason = plain_description(deprecation.get("reason") or "").strip().rstrip("。")
                replacement = deprecation.get("replacement")
                note = "**已废弃**" + (f"：{reason}" if reason else "")
                if replacement:
                    note += f"（改用 `{replacement}`）"
                description = f"{note}。{description}"
            hint = hints.get(prop["name"]) or hints.get(prop["name"] + ".values")
            choices = ", ".join(f"`{value['value']}`" for value in hint.get("values", [])) if hint else ""
            lines.append("| " + " | ".join([
                f"`{prop['name']}`",
                # 类型包在反引号里：List<PreviousKey> 裸写会被 Markdown 渲染器当成 HTML 标签吞掉。
                f"`{cell(simple_type(prop.get('type', '')))}`" if prop.get("type") else "",
                cell(format_default(prop.get("defaultValue"))),
                cell(description),
                cell(choices),
                f"`{prop['artifact']}`",
            ]) + " |")
        lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="生成 chaos 配置参考文档")
    parser.add_argument("--check", action="store_true", help="只校验已提交文档是否与元数据一致，不写文件")
    args = parser.parse_args()

    properties, hints = load_properties(module_directories())
    content = render(properties, hints)
    if args.check:
        current = OUTPUT.read_text(encoding="utf-8") if OUTPUT.is_file() else ""
        if current != content:
            diff = difflib.unified_diff(
                current.splitlines(), content.splitlines(), "committed", "generated", lineterm="", n=1)
            sys.stderr.write("\n".join(list(diff)[:60]) + "\n")
            sys.stderr.write(
                f"\n{OUTPUT.relative_to(ROOT)} 已过期：配置属性或其 Javadoc 发生了变化。\n"
                "请执行 ./mvnw -B -DskipTests compile && python3 scripts/generate-configuration-reference.py 并提交结果。\n")
            return 1
        print(f"{OUTPUT.relative_to(ROOT)} 与配置元数据一致（{len(properties)} 项）")
        return 0
    OUTPUT.write_text(content, encoding="utf-8")
    print(f"已生成 {OUTPUT.relative_to(ROOT)}（{len(properties)} 项）")
    return 0


if __name__ == "__main__":
    sys.exit(main())

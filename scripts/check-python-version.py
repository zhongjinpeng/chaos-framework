#!/usr/bin/env python3
"""校验 chaos-python 包版本与 Java 框架版本一致。

为什么需要：
    chaos-tracing 的版本写在 pyproject.toml 里，Java 框架版本写在 .mvn/maven.config 的 -Drevision 上，
    两者此前没有任何关联，版本各走各的，使用方无法回答“哪个 chaos-tracing 配哪个 chaos 框架”。

用法：
    python3 scripts/check-python-version.py          # 校验，不一致时返回 1
    python3 scripts/check-python-version.py --fix    # 按框架版本改写 pyproject.toml
"""

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAVEN_CONFIG = ROOT / ".mvn/maven.config"
PYPROJECT = ROOT / "chaos-python/chaos-tracing/pyproject.toml"


def framework_version() -> str:
    match = re.search(r"-Drevision=(\S+)", MAVEN_CONFIG.read_text(encoding="utf-8"))
    if not match:
        raise SystemExit(f"在 {MAVEN_CONFIG} 中找不到 -Drevision")
    return match.group(1)


def package_version(content: str) -> str:
    match = re.search(r'^version\s*=\s*"([^"]+)"', content, re.MULTILINE)
    if not match:
        raise SystemExit(f"在 {PYPROJECT} 中找不到 version")
    return match.group(1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fix", action="store_true", help="按框架版本改写 pyproject.toml")
    args = parser.parse_args()

    expected = framework_version()
    content = PYPROJECT.read_text(encoding="utf-8")
    actual = package_version(content)

    if actual == expected:
        print(f"chaos-tracing 版本与框架一致（{expected}）")
        return 0

    if args.fix:
        PYPROJECT.write_text(
            re.sub(r'^version\s*=\s*"[^"]+"', f'version = "{expected}"', content, count=1, flags=re.MULTILINE),
            encoding="utf-8",
        )
        print(f"已把 chaos-tracing 版本从 {actual} 改为 {expected}")
        return 0

    print(
        f"chaos-tracing 版本 {actual} 与框架版本 {expected} 不一致。\n"
        f"执行 python3 scripts/check-python-version.py --fix 修正。",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())

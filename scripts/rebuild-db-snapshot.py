#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
脚本用途：数据库初始化脚本三联一致性校验与数据库快照重建工具
输入参数：
  --check    仅校验 02-schema.sql 与 03-data.sql 结构与数据是否自洽（比对建表列数与 INSERT 字段数）
  --rebuild  校验自洽性，并在校验通过后由 02-schema.sql + 03-data.sql 重建 database/weblog.sql 快照
输出路径：
  - 检查结果输出至标准输出/标准错误
  - 重建输出路径：database/weblog.sql
遵循规范：
  AGENTS.md 规范第 5 条（数据库约定）与第 9 条（SQL与脚本风格约定）
"""

import argparse
import re
import sys
from datetime import datetime
from pathlib import Path

# 保证 Windows 终端下 UTF-8 正常输出
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")


def parse_schema(schema_text: str) -> tuple[dict[str, list[str]], dict[str, str]]:
    """
    解析 02-schema.sql，返回：
    1. tables_cols: {table_name: [col1, col2, ...]}
    2. tables_ddl: {table_name: full_ddl_block_sql}
    """
    tables_cols = {}
    tables_ddl = {}

    # 匹配每个表的完整结构块，形如：
    # -- ----------------------------
    # -- Table structure for t_xxx
    # -- ----------------------------
    # DROP TABLE IF EXISTS `t_xxx`;
    # CREATE TABLE `t_xxx` (
    # ...
    # ) ENGINE = ...;
    table_pattern = re.compile(
        r'(-- -+\n-- Table structure for `?(\w+)`?\n-- -+\nDROP TABLE IF EXISTS `?\2`?;\nCREATE TABLE `?\2`?\s*\((.*?)\)\s*ENGINE\s*=[^;]+;)',
        re.DOTALL | re.IGNORECASE
    )

    for match in table_pattern.finditer(schema_text):
        full_block = match.group(1).strip()
        table_name = match.group(2)
        columns_body = match.group(3)

        columns = []
        for line in columns_body.splitlines():
            line = line.strip()
            if not line or line.startswith('--') or line.startswith('/*'):
                continue
            upper = line.upper()
            if any(upper.startswith(k) for k in [
                'PRIMARY KEY', 'UNIQUE KEY', 'KEY', 'CONSTRAINT',
                'UNIQUE INDEX', 'INDEX', 'FULLTEXT', 'SPATIAL'
            ]):
                continue
            col_match = re.match(r'^`?(\w+)`?\s+', line)
            if col_match:
                columns.append(col_match.group(1))

        tables_cols[table_name] = columns
        tables_ddl[table_name] = full_block

    return tables_cols, tables_ddl


def split_sql_values(values_str: str) -> list[list[str]]:
    """
    状态机切分 SQL VALUES 语句中的多行记录，例如 ((val1, val2), (val3, val4))。
    严格处理单双引号、转义字符及逗号嵌套。
    """
    rows = []
    in_row = False
    in_str = False
    str_char = ''
    escape = False
    current_tokens = []
    current_token = []

    i = 0
    length = len(values_str)
    while i < length:
        c = values_str[i]
        if escape:
            current_token.append(c)
            escape = False
            i += 1
            continue

        if c == '\\':
            escape = True
            current_token.append(c)
            i += 1
            continue

        if in_str:
            if c == str_char:
                # 处理连续引号转义（如 ''）
                if i + 1 < length and values_str[i + 1] == str_char:
                    current_token.append(c)
                    current_token.append(c)
                    i += 2
                    continue
                else:
                    in_str = False
                    current_token.append(c)
            else:
                current_token.append(c)
            i += 1
            continue
        else:
            if c in ("'", '"'):
                in_str = True
                str_char = c
                current_token.append(c)
            elif c == '(':
                in_row = True
                current_tokens = []
                current_token = []
            elif c == ')':
                in_row = False
                token = "".join(current_token).strip()
                if token:
                    current_tokens.append(token)
                rows.append(current_tokens)
                current_tokens = []
                current_token = []
            elif c == ',' and in_row:
                token = "".join(current_token).strip()
                current_tokens.append(token)
                current_token = []
            elif in_row:
                current_token.append(c)
            i += 1

    return rows


def parse_data(data_text: str) -> dict[str, list[str]]:
    """
    解析 03-data.sql，按表提取对应的完整 INSERT 语句列表。
    返回: {table_name: [insert_statement1, ...]}
    """
    table_inserts = {}
    pattern = re.compile(
        r'INSERT\s+INTO\s+`?(\w+)`?(?:\s*\((.*?)\))?\s+VALUES\s*(.*?);',
        re.DOTALL | re.IGNORECASE
    )

    for m in pattern.finditer(data_text):
        stmt = m.group(0).strip()
        tbl = m.group(1)
        if tbl not in table_inserts:
            table_inserts[tbl] = []
        table_inserts[tbl].append(stmt)

    return table_inserts


def check_consistency(repo_root: Path) -> tuple[bool, dict[str, list[str]], dict[str, str], dict[str, list[str]]]:
    """
    执行建表列与 INSERT 值的自洽性检查。
    """
    schema_file = repo_root / "database" / "sql" / "init" / "02-schema.sql"
    data_file = repo_root / "database" / "sql" / "init" / "03-data.sql"

    if not schema_file.exists():
        print(f"[ERROR] 结构初始化文件不存在: {schema_file}", file=sys.stderr)
        return False, {}, {}, {}
    if not data_file.exists():
        print(f"[ERROR] 数据初始化文件不存在: {data_file}", file=sys.stderr)
        return False, {}, {}, {}

    schema_text = schema_file.read_text(encoding="utf-8")
    data_text = data_file.read_text(encoding="utf-8")

    tables_cols, tables_ddl = parse_schema(schema_text)
    table_inserts = parse_data(data_text)

    errors = []
    total_records = 0

    pattern = re.compile(
        r'INSERT\s+INTO\s+`?(\w+)`?(?:\s*\((.*?)\))?\s+VALUES\s*(.*?);',
        re.DOTALL | re.IGNORECASE
    )

    for m in pattern.finditer(data_text):
        table_name = m.group(1)
        col_spec = m.group(2)
        values_block = m.group(3)

        if table_name not in tables_cols:
            errors.append(f"数据文件中的表 '{table_name}' 在 02-schema.sql 中未定义！")
            continue

        schema_cols = tables_cols[table_name]
        expected_count = len(schema_cols)
        if col_spec:
            expected_count = len([c.strip() for c in col_spec.split(',') if c.strip()])

        rows = split_sql_values(values_block)
        for idx, row in enumerate(rows):
            total_records += 1
            if len(row) != expected_count:
                errors.append(
                    f"表 '{table_name}' 第 {idx + 1} 行值数量不匹配: 建表/指定列需 {expected_count} 列 "
                    f"(定义列: {', '.join(schema_cols)})，但 INSERT 实供 {len(row)} 个值！"
                )

    print(f"[CHECK] 已扫描 {len(tables_cols)} 张建表定义，共校验 {total_records} 行 INSERT 数据记录。")
    if errors:
        print(f"\n[FAILED] 发现 {len(errors)} 项结构与数据不一致:", file=sys.stderr)
        for err in errors:
            print(f"  - {err}", file=sys.stderr)
        return False, tables_cols, tables_ddl, table_inserts

    print("[SUCCESS] 02-schema.sql 与 03-data.sql 结构与数据完全自洽！")
    return True, tables_cols, tables_ddl, table_inserts


def rebuild_snapshot(repo_root: Path, tables_ddl: dict[str, str], table_inserts: dict[str, list[str]]) -> bool:
    """
    由 02-schema.sql 和 03-data.sql 重建 database/weblog.sql。
    """
    target_file = repo_root / "database" / "weblog.sql"

    now_str = datetime.now().strftime("%d/%m/%Y %H:%M:%S")

    header = f"""/*
 Navicat Premium Dump SQL

 Source Server         : localhost_3307
 Source Server Type    : MySQL
 Source Server Version : 80036 (8.0.36)
 Source Host           : localhost:3307
 Source Schema         : weblog

 Target Server Type    : MySQL
 Target Server Version : 80036 (8.0.36)
 File Encoding         : 65001

 Date: {now_str}

 ⚠️ 警告：本文件包含测试数据（测试用户、测试文章等），仅供开发和测试环境使用
    生产环境请勿直接导入，应使用 Flyway 迁移脚本初始化数据库
 SQL_TEST_DATA_WARNING
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
"""

    sections = [header.strip()]

    for table_name, ddl_block in tables_ddl.items():
        parts = [ddl_block]
        records_header = f"-- ----------------------------\n-- Records of {table_name}\n-- ----------------------------"
        inserts = table_inserts.get(table_name, [])
        if inserts:
            parts.append(records_header + "\n" + "\n".join(inserts))
        else:
            parts.append(records_header)
        sections.append("\n\n".join(parts))

    footer = "SET FOREIGN_KEY_CHECKS = 1;\n"
    content = "\n\n".join(sections) + "\n\n" + footer

    target_file.write_text(content, encoding="utf-8")
    print(f"[REBUILD] 数据库快照重建成功 -> {target_file}")
    print(f"[REBUILD] 共合并 {len(tables_ddl)} 张表结构及其对应的初始化数据。")
    return True


def main():
    parser = argparse.ArgumentParser(
        description="数据库初始化脚本三联一致性校验与快照重建工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python scripts/rebuild-db-snapshot.py --check    # 仅比对校验建表列与数据值自洽性
  python scripts/rebuild-db-snapshot.py --rebuild  # 校验通过后重建 database/weblog.sql
"""
    )
    parser.add_argument("--check", action="store_true", help="校验 02-schema.sql 与 03-data.sql 是否自洽")
    parser.add_argument("--rebuild", action="store_true", help="自洽校验通过后，由 init 脚本重建 database/weblog.sql")

    args = parser.parse_args()

    if not args.check and not args.rebuild:
        parser.print_help()
        sys.exit(1)

    repo_root = Path(__file__).resolve().parent.parent

    passed, tables_cols, tables_ddl, table_inserts = check_consistency(repo_root)
    if not passed:
        sys.exit(1)

    if args.rebuild:
        success = rebuild_snapshot(repo_root, tables_ddl, table_inserts)
        if not success:
            sys.exit(1)


if __name__ == "__main__":
    main()

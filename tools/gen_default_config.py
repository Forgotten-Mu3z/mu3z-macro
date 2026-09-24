#!/usr/bin/env python3
"""Regenerates src/main/resources/altartestclient-default.json from the setting declarations in the Java
sources, so the bundled default config always matches the code defaults. Run from the repo root:

    python3 tools/gen_default_config.py          # rewrite the file
    python3 tools/gen_default_config.py --check  # exit 1 if the file is out of date
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/net/altarsmp/testclient"
OUT = ROOT / "src/main/resources/altartestclient-default.json"

SETTING = re.compile(
    r'new (BooleanSetting|IntSetting|DoubleSetting|EnumSetting<>)\(\s*"([^"]+)",\s*"[^"]*",\s*"(?:[^"\\]|\\.)*",\s*([^,)]+)',
    re.S)


def parse_value(kind, raw):
    raw = raw.strip()
    if kind == "BooleanSetting":
        return raw == "true"
    if kind == "IntSetting":
        return int(raw)
    if kind == "DoubleSetting":
        return float(raw)
    return raw.split(".")[-1]  # EnumSetting: SomeEnum.CONSTANT -> "CONSTANT"


def settings_of(text):
    return [(key, parse_value(kind, raw)) for kind, key, raw in SETTING.findall(text)]


def main():
    manager = (SRC / "module/ModuleManager.java").read_text()
    order = re.findall(r"register\(new (\w+)\(\)\)", manager)
    module_files = {p.stem: p for p in SRC.glob("module/*/*.java")}

    glob_text = (SRC / "config/GlobalConfig.java").read_text()
    servers = re.search(r"DEFAULT_ALLOWED_SERVERS = List\.of\(([^)]*)\)", glob_text).group(1)
    global_obj = {"allowedServers": re.findall(r'"([^"]+)"', servers)}
    global_obj.update(settings_of(glob_text))

    modules = {}
    for cls in order:
        text = module_files[cls].read_text()
        module_id = re.search(r'super\("([^"]+)"', text).group(1)
        obj = {"enabled": False, "speed": "HUMANLIKE"}
        obj.update(settings_of(text))
        modules[module_id] = obj

    config = {"configVersion": 1, "global": global_obj, "modules": modules}
    rendered = json.dumps(config, indent=2) + "\n"
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text() != rendered:
            print(f"{OUT.relative_to(ROOT)} is out of date; run tools/gen_default_config.py")
            sys.exit(1)
        print("default config is up to date")
        return
    OUT.write_text(rendered)
    print(f"wrote {OUT.relative_to(ROOT)} ({len(modules)} modules)")


if __name__ == "__main__":
    main()

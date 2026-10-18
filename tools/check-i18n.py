#!/usr/bin/env python3
"""Check Android string catalogs for parity, placeholders, and locale coverage."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
PLACEHOLDER = re.compile(r"%(\d+)\$([a-zA-Z])")


def fail(message: str) -> None:
    print(f"i18n check failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def parse_catalog(path: Path) -> dict[str, ET.Element]:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        fail(f"invalid XML in {path.relative_to(ROOT)}: {exc}")
    entries: dict[str, ET.Element] = {}
    for element in root:
        if element.tag not in {"string", "plurals"}:
            continue
        name = element.get("name")
        if not name:
            fail(f"resource without a name in {path.relative_to(ROOT)}")
        if name in entries:
            fail(f"duplicate resource {name!r} in {path.relative_to(ROOT)}")
        if element.tag == "plurals":
            quantities = [item.get("quantity") for item in element.findall("item")]
            if len(quantities) != len(set(quantities)):
                fail(f"duplicate plural quantity in {name!r} in {path.relative_to(ROOT)}")
            if "other" not in quantities:
                fail(f"plural {name!r} has no 'other' item in {path.relative_to(ROOT)}")
        entries[name] = element
    return entries


def values_of(element: ET.Element) -> list[str]:
    if element.tag == "string":
        return [element.text or ""]
    return [item.text or "" for item in element.findall("item")]


def placeholder_signature(text: str) -> tuple[tuple[str, str], ...]:
    return tuple(sorted(PLACEHOLDER.findall(text)))


def locale_tag(folder: str) -> str:
    qualifier = folder.removeprefix("values-")
    return re.sub(r"-r([A-Z]{2})$", r"-\1", qualifier)


def main() -> None:
    base_path = RES / "values" / "strings.xml"
    base = parse_catalog(base_path)
    expected = {
        key: element
        for key, element in base.items()
        if not (element.tag == "string" and element.get("translatable") == "false")
    }
    if not expected:
        fail("the default catalog has no translatable resources")

    for key, element in expected.items():
        for value in values_of(element):
            if not value.strip():
                fail(f"empty default value for {key}")

    config_path = RES / "xml" / "locales_config.xml"
    try:
        locale_config = ET.parse(config_path).getroot()
    except ET.ParseError as exc:
        fail(f"invalid XML in {config_path.relative_to(ROOT)}: {exc}")
    declared: set[str] = set()
    for locale in locale_config.findall("locale"):
        tag = locale.get(ANDROID_NS + "name")
        if not tag:
            fail("locale_config contains an empty locale name")
        if tag in declared:
            fail(f"duplicate locale {tag!r} in locale_config.xml")
        declared.add(tag)

    catalogs = sorted(RES.glob("values-*/strings.xml"))
    resource_locales = {locale_tag(path.parent.name): path for path in catalogs}
    if "en" not in declared:
        fail("locale_config.xml must declare the default English locale")
    missing_locales = set(resource_locales) - declared
    missing_catalogs = declared - set(resource_locales) - {"en"}
    if missing_locales or missing_catalogs:
        fail(
            "locale coverage mismatch; undeclared resource locales: "
            f"{sorted(missing_locales)}, locales without resource catalogs: "
            f"{sorted(missing_catalogs)}"
        )

    for tag, path in resource_locales.items():
        catalog = parse_catalog(path)
        missing = set(expected) - set(catalog)
        extra = set(catalog) - set(expected)
        if missing or extra:
            fail(
                f"{tag}: missing keys {sorted(missing)}, "
                f"unexpected keys {sorted(extra)}"
            )
        for key, base_element in expected.items():
            element = catalog[key]
            if element.tag != base_element.tag:
                fail(f"{tag}: {key} changes resource type {base_element.tag} -> {element.tag}")
            translated_values = values_of(element)
            if not translated_values or any(not value.strip() for value in translated_values):
                fail(f"{tag}: {key} has an empty value")
            base_values = values_of(base_element)
            signature = placeholder_signature(base_values[-1])
            for value in translated_values:
                if placeholder_signature(value) != signature:
                    fail(
                        f"{tag}: {key} has placeholders "
                        f"{placeholder_signature(value)}, expected {signature}"
                    )

        preview = catalog.get("preview_default_text")
        preview_text = preview.text or "" if preview is not None else ""
        if "Aa Bb Cc" not in preview_text or r"\n" not in preview_text:
            fail(f"{tag}: preview_default_text must preserve a Latin sample and escaped line breaks")

    total_locales = len(resource_locales) + 1  # English comes from values/.
    print(
        f"Validated {total_locales} locales, {len(expected)} translatable resources, "
        "resource parity, non-empty values, and format placeholders."
    )


if __name__ == "__main__":
    main()

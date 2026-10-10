#!/usr/bin/env python3
"""Refreshes app/src/main/assets/offices.json.

The file is bundled with the app. It gives the map a position for every
passport/ID office and works as an offline fallback for the office list.

  * Offices: the police's public appointment booking (the same data the
    booking page at pass-og-id.politiet.no uses), for every service.
  * Coordinates: Kartverket's open address API (ws.geonorge.no), falling back
    to the postcode and then the place name when an address isn't found.

Usage:  python tools/update_offices.py [--fresh]
"""
import json
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

QMATIC = "https://pass-og-id.politiet.no/qmaticwebbooking/rest/schedule/"
GEONORGE = "https://ws.geonorge.no/"
USER_AGENT = "Passradar office list updater (open source)"
OUT = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "offices.json"


def get_json(url, attempts=4):
    # The firewall in front of the booking system sometimes resets the connection
    # for large answers such as the office list; waiting a bit usually helps.
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except (urllib.error.URLError, ConnectionError, TimeoutError) as error:
            if attempt == attempts - 1:
                raise
            wait = 20 * (attempt + 1)
            print(f"  {error.__class__.__name__}, retrying in {wait} s", file=sys.stderr)
            time.sleep(wait)


def as_list(data):
    return data["value"] if isinstance(data, dict) else data


def clean(text):
    return " ".join((text or "").replace(" ,", ",").split()).strip(" ,")


def nice_city(text):
    text = clean(text)
    return text.title() if text.isupper() else text


def lookup_address(params):
    params = {**params, "treffPerSide": "1", "utkoordsys": "4258"}
    hits = get_json(GEONORGE + "adresser/v1/sok?" + urllib.parse.urlencode(params)).get("adresser") or []
    time.sleep(0.15)
    if not hits:
        return None
    point = hits[0]["representasjonspunkt"]
    return point["lat"], point["lon"]


def lookup_place(name, office_name):
    params = {"sok": name, "treffPerSide": "10", "utkoordsys": "4258"}
    places = get_json(GEONORGE + "stedsnavn/v1/sted?" + urllib.parse.urlencode(params)).get("navn") or []
    time.sleep(0.15)
    if not places:
        return None

    # Many place names exist in several municipalities ("Fiskå", "Vik"). Prefer
    # the one whose municipality is part of the office name, then towns.
    def score(place):
        municipalities = [m.get("kommunenavn", "") for m in place.get("kommuner", [])]
        in_name = any(m and m.lower() in office_name.lower() for m in municipalities)
        is_town = place.get("navneobjekttype") in ("By", "Tettsted", "Tettbebyggelse", "Bydel")
        return (not in_name, not is_town)

    point = min(places, key=score)["representasjonspunkt"]
    return point.get("nord"), point.get("øst")


def geocode(street, zip_code, city, office_name):
    attempts = []
    if street:
        attempts.append({"sok": street, "postnummer": zip_code})
        parts = [p.strip() for p in street.split(",") if p.strip()]
        if len(parts) > 1:
            attempts += [{"sok": p, "postnummer": zip_code} for p in parts]
        attempts.append({"sok": street, "postnummer": zip_code, "fuzzy": "true"})
    for params in attempts:
        found = lookup_address(params)
        if found:
            return found, "address"
    if zip_code:
        found = lookup_address({"postnummer": zip_code})
        if found:
            return found, "postcode"
    if city:
        found = lookup_place(city, office_name)
        if found:
            return found, "place"
    return None, "missing"


def main():
    fresh = "--fresh" in sys.argv  # geocode everything again instead of reusing positions
    services = get_json(QMATIC + "services;validate=true")
    offices, offered = {}, {}
    for service in services:
        for branch in as_list(get_json(QMATIC + "branches/available;servicePublicId=" + service["publicId"])):
            offices.setdefault(branch["id"], branch)
            offered.setdefault(branch["id"], set()).add(service["publicId"])
        time.sleep(2)
    print(f"{len(services)} services, {len(offices)} offices")

    previous = {}
    if OUT.exists() and not fresh:
        previous = {o["id"]: o for o in json.loads(OUT.read_text(encoding="utf-8"))}

    result, sources = [], {}
    for branch in offices.values():
        street = clean(branch.get("addressLine1"))
        zip_code = clean(branch.get("addressZip"))
        city = nice_city(branch.get("addressCity"))
        old = previous.get(branch["id"])
        if old and old.get("street") == street and old.get("zip") == zip_code and old.get("lat") is not None:
            position, source = (old["lat"], old["lon"]), "cached"
        else:
            position, source = geocode(street, zip_code, city, clean(branch["name"]))
        sources[source] = sources.get(source, 0) + 1
        if source in ("postcode", "place", "missing"):
            print(f"  {source:<8} {branch['name']}: {street}, {zip_code} {city}")
        result.append({
            "id": branch["id"],
            "name": clean(branch["name"]),
            "street": street,
            "zip": zip_code,
            "city": city,
            "lat": round(position[0], 5) if position else None,
            "lon": round(position[1], 5) if position else None,
            "services": sorted(offered[branch["id"]]),
        })

    result.sort(key=lambda o: o["name"])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"Wrote {len(result)} offices to {OUT} ({sources})")


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""
Valhalla Offline Tiles Builder for Femto Car Launcher.
Converts any OpenStreetMap extract (.osm.pbf) into Valhalla routing tiles (.tar).
"""

import sys
import os
import subprocess
import json

def build_tiles(pbf_path, output_dir="valhalla_tiles"):
    if not os.path.exists(pbf_path):
        print(f"Error: PBF file not found: {pbf_path}")
        sys.exit(1)

    os.makedirs(output_dir, exist_ok=True)
    tiles_dir = os.path.join(output_dir, "tiles")
    os.makedirs(tiles_dir, exist_ok=True)
    
    config_path = os.path.join(output_dir, "valhalla.json")
    script_dir = os.path.dirname(os.path.abspath(__file__))
    template_path = os.path.join(script_dir, "valhalla_template.json")

    with open(template_path, "r", encoding="utf-8") as f:
        config = json.load(f)

    config["mjolnir"]["tile_dir"] = os.path.abspath(tiles_dir).replace("\\", "/")
    config["mjolnir"]["tile_extract"] = os.path.abspath(os.path.join(output_dir, "valhalla_tiles.tar")).replace("\\", "/")

    with open(config_path, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2)

    print(f"Generating Valhalla tiles from {pbf_path}...")
    cmd = ["valhalla_build_tiles", "-c", config_path, pbf_path]
    res = subprocess.run(cmd)
    if res.returncode != 0:
        print("Tile generation failed.")
        sys.exit(res.returncode)

    print("Packaging tiles into valhalla_tiles.tar...")
    tar_cmd = ["valhalla_build_extract", "-c", config_path, "-v"]
    res = subprocess.run(tar_cmd)
    if res.returncode == 0:
        print(f"Success! Valhalla tiles archive created at {config['mjolnir']['tile_extract']}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python build_tiles.py <region.osm.pbf> [output_directory]")
        sys.exit(1)
    out = sys.argv[2] if len(sys.argv) > 2 else "valhalla_tiles"
    build_tiles(sys.argv[1], out)

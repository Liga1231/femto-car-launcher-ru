#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Valhalla Offline Routing Tiles Builder for Femto Car Launcher.
Converts any BBBike / OpenStreetMap extract (.osm.pbf) into Valhalla routing archive (valhalla_tiles.tar).

Supports:
  1. Docker (Recommended, zero local Valhalla compilation needed, works on Windows/macOS/Linux).
  2. Local Valhalla CLI (valhalla_build_tiles + valhalla_build_extract if installed).
"""

import sys
import os
import shutil
import subprocess
import json
import argparse

def check_docker():
    try:
        res = subprocess.run(["docker", "--version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        return res.returncode == 0
    except Exception:
        return False

def check_valhalla_cli():
    return shutil.which("valhalla_build_tiles") is not None

def build_with_docker(pbf_abs_path, output_dir_abs):
    print("-> Detected Docker. Using official Valhalla container (ghcr.io/gis-ops/docker-valhalla/valhalla:latest)...")
    
    pbf_dir = os.path.dirname(pbf_abs_path)
    pbf_name = os.path.basename(pbf_abs_path)
    
    # Valhalla docker container mounts custom_files to /custom_files
    # It builds valhalla_tiles.tar automatically if valhalla.json is present
    custom_files = os.path.abspath(output_dir_abs)
    os.makedirs(custom_files, exist_ok=True)
    
    # Copy or link pbf into custom_files
    dest_pbf = os.path.join(custom_files, pbf_name)
    if os.path.abspath(dest_pbf) != pbf_abs_path:
        print(f"-> Copying {pbf_name} to work directory...")
        shutil.copy2(pbf_abs_path, dest_pbf)
        
    docker_cmd = [
        "docker", "run", "--rm",
        "-v", f"{custom_files}:/custom_files",
        "ghcr.io/gis-ops/docker-valhalla/valhalla:latest"
    ]
    
    print(f"-> Running Valhalla build: {' '.join(docker_cmd)}")
    res = subprocess.run(docker_cmd)
    if res.returncode != 0:
        print(f"Error: Docker build exited with code {res.returncode}")
        return False
        
    tar_path = os.path.join(custom_files, "valhalla_tiles.tar")
    if os.path.exists(tar_path):
        print(f"\n[SUCCESS] valhalla_tiles.tar generated successfully at:\n  {tar_path}")
        print("\nNext step: Copy valhalla_tiles.tar to your phone:")
        print("  /sdcard/Android/data/io.github.seijikohara.femto/files/maps/valhalla_tiles.tar")
        return True
    else:
        print(f"Error: {tar_path} was not created.")
        return False

def build_with_local_cli(pbf_abs_path, output_dir_abs):
    print("-> Using local Valhalla CLI tools...")
    tiles_dir = os.path.join(output_dir_abs, "tiles")
    os.makedirs(tiles_dir, exist_ok=True)
    
    config_path = os.path.join(output_dir_abs, "valhalla.json")
    script_dir = os.path.dirname(os.path.abspath(__file__))
    parent_tools = os.path.dirname(script_dir)
    template_path = os.path.join(parent_tools, "valhalla", "valhalla_template.json")
    
    if os.path.exists(template_path):
        with open(template_path, "r", encoding="utf-8") as f:
            config = json.load(f)
    else:
        # Generate default config via valhalla_build_config
        cfg_res = subprocess.run(["valhalla_build_config", "--mjolnir-tile-dir", tiles_dir], stdout=subprocess.PIPE, text=True)
        config = json.loads(cfg_res.stdout)

    tar_path = os.path.join(output_dir_abs, "valhalla_tiles.tar")
    config["mjolnir"]["tile_dir"] = os.path.abspath(tiles_dir).replace("\\", "/")
    config["mjolnir"]["tile_extract"] = os.path.abspath(tar_path).replace("\\", "/")

    with open(config_path, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2)

    print("-> Running valhalla_build_tiles...")
    cmd1 = ["valhalla_build_tiles", "-c", config_path, pbf_abs_path]
    r1 = subprocess.run(cmd1)
    if r1.returncode != 0:
        print("Error during valhalla_build_tiles")
        return False

    print("-> Running valhalla_build_extract...")
    cmd2 = ["valhalla_build_extract", "-c", config_path, "-v"]
    r2 = subprocess.run(cmd2)
    if r2.returncode == 0 and os.path.exists(tar_path):
        print(f"\n[SUCCESS] valhalla_tiles.tar generated successfully at:\n  {tar_path}")
        print("\nNext step: Copy valhalla_tiles.tar to your phone:")
        print("  /sdcard/Android/data/io.github.seijikohara.femto/files/maps/valhalla_tiles.tar")
        return True
    return False

def main():
    parser = argparse.ArgumentParser(description="Build Valhalla offline navigation tiles (.tar) from BBBike OSM PBF file.")
    parser.add_argument("pbf", help="Path to downloaded BBBike extract (e.g. planet_55.12,37.20.osm.pbf)")
    parser.add_argument("-o", "--output", default="valhalla_output", help="Output directory (default: ./valhalla_output)")
    args = parser.parse_args()

    pbf_abs = os.path.abspath(args.pbf)
    if not os.path.exists(pbf_abs):
        print(f"Error: File not found: {args.pbf}")
        sys.exit(1)

    out_abs = os.path.abspath(args.output)
    os.makedirs(out_abs, exist_ok=True)

    print(f"=== Femto Valhalla Tile Builder ===")
    print(f"Source PBF: {pbf_abs}")
    print(f"Output dir: {out_abs}")

    if check_docker():
        success = build_with_docker(pbf_abs, out_abs)
    elif check_valhalla_cli():
        success = build_with_local_cli(pbf_abs, out_abs)
    else:
        print("\n[ERROR] Neither Docker nor local Valhalla CLI tools were found!")
        print("To generate tiles, please either:")
        print("  1. Install Docker Desktop (easiest): https://www.docker.com/products/docker-desktop/")
        print("     and start it before running this script.")
        print("  2. Or use WSL2 / Linux with valhalla installed (sudo apt install valhalla-bin).")
        sys.exit(1)

    if not success:
        sys.exit(1)

if __name__ == "__main__":
    main()

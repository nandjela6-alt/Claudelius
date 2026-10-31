#!/usr/bin/env bash
# Connect over Wi-Fi ADB and take a first screenshot to prove it works.
source "$(dirname "$0")/lib.sh"
connect
echo "Connected: $(tsh getprop ro.product.manufacturer) $(tsh getprop ro.product.model), Android $(tsh getprop ro.build.version.release) (API $(tsh getprop ro.build.version.sdk))"
shot connected

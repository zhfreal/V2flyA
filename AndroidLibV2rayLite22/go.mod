module github.com/2dust/AndroidLibV2rayLite

go 1.15

require (
	golang.org/x/mobile v0.0.0-20200329125638-4c31acba0007
	golang.org/x/sys v0.0.0-20201107080550-4d91cf3a1aaf
	v2ray.com/core v4.19.1+incompatible
)

replace v2ray.com/core => github.com/v2fly/v2ray-core v1.24.5-0.20201109145237-553bf3368bd8

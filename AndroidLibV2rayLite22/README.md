# AndroidLibV2rayLite

构建：切换到此目录后执行

```bash
go get
gomobile init
gomobile bind -target android
```

然后请耐心等待构建过程。当然，首先你需要 go 环境，以及 Android NDK，两者缺一不可。详细安装方法请自行 Google。

构建完成后会生成文件`libv2ray.aar`，将此文件复制到`V2flyA-app/libv2ray/`即可运行。


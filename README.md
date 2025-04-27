# rtd1296-Android7
**准备工作**

  下载docker编译环境 [Docker Hub](https://hub.docker.com/repository/docker/3298837796/android7)

  下载源码zip包并导入到docker容器内

  使用ssh 连接android7编译容器
  
---------
**编译**

编译LK

    $ cd LK
    $ ./build_rtk_lk.sh rtd1295
    $ cp bootloader_lk.tar ../Openwrt/target/linux/rtd1295/image/image_file-r160868/packages/omv/

编译安装包

    $ ./build_android.sh build
    $ ./build_openwrt.sh build

安装包输出目录: Openwrt/bin/rtd1295-glibc/install.img; android 编译输出目录:android/out/target/product/kylin32

---------

**定制参数**

    $ cd Openwrt
    $ make menuconfig

会弹出编译定制框

# README #

This README is for the user who

* Want to develop application with LIPS 3D depth camera.
* Already has a LIPS camera module on hand.
* The target platform is Android
* Develop Android application with OpenNI 1.5
* Use Android Studio IDE

If you don't have one and are interesting with it, please contact with us by e-mail [info@lips-hci.com](mailto:info@lips-hci.com)

# Download LIPS Sample Code #

## Install GIT ##

```
sudo apt-get install git-core
```

## Clone Sample code ##

Clone the project.

```
git clone https://github.com/lips-hci/openni_android.git LIPS_Sample
```

## Replace Library ##

Please contact with us by e-mail [info@lips-hci.com](mailto:info@lips-hci.com) to have latest library.

> ## Note: If you are using HL1 camera, below operations are needed. ##
>
> ### Replace .so for HL1
>
>Under path: SimpleRead/app/src/main/jniLibs/armeabi-v7a
>
>Replace below .so files
>
>	`libbilateral.so`
>
>	`libdevicesensor.so`
>
>	`libmodule-lips.so`
>
>with
>
>	`libCommonUtilities.so`
>
>	`libInuStreams.so`
>
>	`libgnustl_shared.so`
>
>	`libinusensor.so`
>
>	`libmodule-lips-hl1.so`
>
> ### Modify modules.xml ###
>
>Modify file modules.xml under path: SimpleRead/app/src/main/assets/openni/
>
>Replace
>
>`<Module path="libmodule-lips.so" />`
>
>with
>
>`<Module path="libmodule-lips-hl1.so" />`

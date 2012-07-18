The Rustici Book Scanner App relies on a third party library (or a separate app install). In order to build this app, you will need to get the ZXing Library (http://code.google.com/p/zxing/) and follow the instructions they provide for linking the libraries and creating the core.jar file.

This is a required dependency.  With a small amount of effort, the ZXing Library can be detached and instead require a Barcode Scanner based off the ZXing Library to installed on the device.

Simply create an Android Project and overwrite res, src, and AndroidManifest.xml.  The rest of the files should be generated automatically.

A few setting files are provided (but likely unnecessary).  This project requires a compiler compliance to Java 1.6.  It uses SDK level 7.
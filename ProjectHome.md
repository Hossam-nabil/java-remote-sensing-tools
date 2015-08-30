This project is little more than an online notebook.  It is mostly a collection of static methods I use to solve a wide variety of problems in scientific computing using remotely sensed data.  These tools were created to support research in remote sensing and/or sensor maintenance and operations.  Generally, the main() method is the repository of the processing that links together the methods for specific purposes.  Sometimes, a processing class extends the methods class and just contains main(), the code notebook.  The methods address subjects ranging over segmentation accuracy metrics, sensor calibration, atmospheric correction, phenology, image classification, etc.  Generally, pixel access is provided by Java Advanced Imaging, vector data is handled by GeoTools, math is handled by Apache Commons Math, and Weka is used as the data mining and prediction engine.  It's possible you can find a little method that does exactly what you need.  However, it's not meant to be a tidy package with a nice GUI that you simply download and open, some code is old and broken, some is unfinished, there are dead ends, etc.  Please browse with caution.

That said, here are some places with general methods that may be useful:

Basic utilities:
https://code.google.com/p/java-remote-sensing-tools/source/browse/trunk/Open/src/com/berkenviro/imageprocessing/Utils.java

Atmospheric correction using Modtran: radiance to reflectance or temperature,
https://code.google.com/p/java-remote-sensing-tools/source/browse/trunk/Open/src/com/berkenviro/imageprocessing/ATMcorr.java
https://code.google.com/p/java-remote-sensing-tools/source/browse/trunk/Open/src/com/berkenviro/imageprocessing/MODTRANprocessor.java

Spectral response function utilities: mean, FWHM, Gaussian fitting,...
https://code.google.com/p/java-remote-sensing-tools/source/browse/trunk/Open/src/com/berkenviro/imageprocessing/SRFUtils.java

Time series: smoothing, interpolating, etc.
https://code.google.com/p/java-remote-sensing-tools/source/browse/trunk/Open/src/cn/edu/tsinghua/timeseries/TSUtils.java

Image processing: mostly pixel access, stats, projection information
https://code.google.com/p/java-remote-sensing-tools/source/browse/trunk/Open/src/com/berkenviro/imageprocessing/JAIUtils.java

Image classification using Weka and JAI:
https://code.google.com/p/java-remote-sensing-tools/source/browse/trunk/Open/src/com/berkenviro/imageprocessing/ImageClassifier2.java

Segmentation accuracy measures:
https://code.google.com/p/java-remote-sensing-tools/source/browse/trunk/Open/src/com/berkenviro/segmentation/IntersectorGUI.java

GIS: basic feature creation, coordinate conversion
https://code.google.com/p/java-remote-sensing-tools/source/browse/trunk/Open/src/com/berkenviro/gis/GISUtils.java

Weka data mining utilities:
https://code.google.com/p/java-remote-sensing-tools/source/browse/trunk/Open/src/com/berkenviro/imageprocessing/WekaUtils.java


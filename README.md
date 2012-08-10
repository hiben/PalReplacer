PalReplacer
===========

A batch-palette replacer

takes a list of images and writes new images using a given palette keeping palette order

The basic idea is that you have a number of images in an indexed color graphics format (like Gif) and now you
need them all with the exact same palette where exact also means same order of colors. Normally this would
be a job for ImageMagick but palette order keeping is not (yet ?) implemented.

Written in Java 1.6, tested with IcedTea. The ant build creates a runnable Jar by default.

more documentation to come - but the software is very simple. Works for most cases - needs testing for border-cases...

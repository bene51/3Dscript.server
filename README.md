3Dscript.server
===============

Fiji plugins for rendering 3D/4D animations via [3Dscript](https://bene51.github.com/3Dscript) remotely using a client-server architecture. This repository contains a server plugin intended to run as a service on a shared workstation and a client plugin which submits jobs from low-end client machines to the server for rendering with 3Dscript.

Installation
------------
Installation of client and server plugin is identical:
* Start Fiji
* Click on Help>Update
* Click on "Manage update sites"
* If 3Dscript is not yet installed, check the box in front of "3Dscript"
* Click on "Add update site"
* Enter Name: 3Dscript.server
* Enter URL: https://romulus.oice.uni-erlangen.de/imagej/updatesites/3Dscript-server/
* Click on "Close"
* Click on "Apply changes"
* Restart Fiji

Usage
-----
Find detailed usage information in the [wiki](https://github.com/bene51/3Dscript.server/wiki).

Links
-----
More information about 3Dscript:
* https://bene51.github.io/3Dscript
* https://github.com/bene51/3Dscript

Running 3Dscript.server as OMERO.web app:
* https://github.com/bene51/omero_3Dscript

Running 3Dscript.server on a cluster:
* TODO

Publication:
------------
Schmid, B.; Tripal, P. & Fraa&szlig;, T. et al. (2019), "3Dscript: animating 3D/4D microscopy data using a natural-language-based syntax", _Nature methods_ **16(4)**: 278-280, PMID 30886414.



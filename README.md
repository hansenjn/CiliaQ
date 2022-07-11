# ![CiliaQ](https://github.com/hansenjn/CiliaQ/blob/master/Webfiles/20200618%20CiliaQ%20Logo%20Small.png?raw=true)
An set of three ImageJ plugins to quantify ciliary shape, length, and fluorescence in 2D, 3D, and 4D images. Scroll down for information on how to [use](https://github.com/hansenjn/CiliaQ#using-ciliaq), [cite](https://github.com/hansenjn/CiliaQ#how-to-cite), [report ideas, issues, improve](https://github.com/hansenjn/CiliaQ#ideas-missing-functions-issues-bugs-unclear-in-the-user-guide) CiliaQ. Visit our [CiliaQ wiki](https://github.com/hansenjn/CiliaQ/wiki/Home/) with Tutorials and a Q&A section or [try out](https://github.com/hansenjn/CiliaQ/tree/master/Examples) CiliaQ using an example image. 

The ImageJ plugins are published along:

Jan N. Hansen, Sebastian Rassmann, Birthe Stueven, Nathalie Jurisch-Yaksi, Dagmar Wachten. CiliaQ: a simple, open-source software for automated quantification of ciliary morphology and fluorescence in 2D, 3D, and 4D images. Eur. Phys. J. E 44, 18 (2021). https://doi.org/10.1140/epje/s10189-021-00031-y

## Included tools (ImageJ plugins)
- **CiliaQ_Preparator**: An ImageJ plugin to preprocess and segment images for CiliaQ analysis.
- **CiliaQ_Editor**: An ImageJ plugin to edit the segmented channel in images output by CiliaQ_Preparator before analysis with CiliaQ.
- **CiliaQ**: An ImageJ plugin to quantify the ciliary shape, length, and fluorescence in images that were pre-processed with CiliaQ_Preparator (and eventually edited with CiliaQ_Editor).

## Tools for post-hoc analysis of the output data
See our [R scripts](https://github.com/hansenjn/CiliaQ/tree/master/R%20Scripts) for combining results produced with CiliaQ.

## How to cite?
When using any of the CiliaQ plugins, please cite:

Jan N. Hansen, Sebastian Rassmann, Birthe Stueven, Nathalie Jurisch-Yaksi, Dagmar Wachten. CiliaQ: a simple, open-source software for automated quantification of ciliary morphology and fluorescence in 2D, 3D, and 4D images. Eur. Phys. J. E 44, 18 (2021). https://doi.org/10.1140/epje/s10189-021-00031-y

## Copyright notice and contacts
Copyright (C) 2017-2021: Jan N. Hansen. 

CiliaQ has been developed in the research group Biophysical Imaging, Institute of Innate Immunity, Bonn, Germany (https://www.iiibonn.de/dagmar-wachten-lab/dagmar-wachten-lab-science).

The project was mainly funded by the [DFG priority program SPP 1726 "Microswimmers"](https://www.fz-juelich.de/ibi/ibi-5//EN/Leistungen/SPP1726/_node.html).

Contacts: 
- jan.hansen (at) uni-bonn.de
- dwachten (at) uni-bonn.de

## Licenses
The three CiliaQ plugins are published under the [GNU General Public License v3.0](https://github.com/hansenjn/CiliaQ/blob/master/LICENSE).

Some CiliaQ plugins include packages developed by others, for which different licenses may apply:
- The packages [ciliaQ_skeleton_analysis](https://github.com/hansenjn/CiliaQ/tree/master/src/main/java/ciliaQ_skeleton_analysis) and [ciliaQ_skeletonize3D](https://github.com/hansenjn/CiliaQ/tree/master/src/main/java/ciliaQ_skeletonize3D) in CiliaQ have been derived from the plugins AnalyzeSkeleton_ and Skeletonize3D_, respectively (Both: GNU General Public License, http://www.gnu.org/licenses/gpl.txt, author: Ignacio Arganda-Carreras).
- The package [ciliaQ_jnh.volumeViewer3D ](https://github.com/hansenjn/CiliaQ/tree/master/src/main/java/ciliaQ_jnh/volumeViewer3D) in CiliaQ represents a customised version of the code from Volume Viewer 2.0 (author: Kai Uwe Barthel, date: 01.12.2012). Customised variants of the code are marked by additional comments. The original code was retrieved from https://github.com/fiji/Volume_Viewer, which is published under the license "Public Domain" in the software project FIJI.
- The package [ciliaQ_Prep_jnh.canny3d-thresholder](https://github.com/hansenjn/CiliaQ_Preparator/tree/master/src/main/java/ciliaQ_Prep_jnh/canny3d_thresholder) in CiliaQ Preparator represents a customized version of the canny3d-thresholder (GNU General Public License v3.0, author: Sebastian Rassmann, GitHub repository: https://github.com/sRassmann/canny3d-thresholder).

Some functions of CiliaQ Preparator (Hysteresis thresholding, Canny3D) require additional installation of the "core" and "plugins" file from the ['3D ImageJ Suite'](https://mcib3d.frama.io/3d-suite-imagej/) to your ImageJ / FIJI distribution. The '3D ImageJ Suite' is licensed via a GPL - for license details visit the main page of the ['3D ImageJ Suite'](https://mcib3d.frama.io/3d-suite-imagej/).

## Using CiliaQ
### System requirements
#### Hardware requirements
ImageJ/FIJI does not require any specific hardware and can also run on low-performing computers. However, a RAM is required that allows to load one image sequence that you aim to analyze into your RAM at least twice. ImageJ does not require any specific graphics card. The speed of the analysis depends mainly on the processor speed. Only the generation of 3D visualizations, an optional function of CiliaQ, will use the graphics card of the computer.

#### Operating system
The ImageJ plugins were developed and tested on Windows 8.1, Windows 10, and macOS Catalina (version 10.15.6). ImageJ is also available for Linux operating systems, where the ImageJ plugins and Java software in theory can be equally run.

#### Software requirements
Performing the analysis pipeline requires the installation of
- [ImageJ](https://imagej.net/Downloads) (tested on versions 1.51r, 1.51u, and 1.52i) or ideally, the ImageJ distribution [Fiji](https://imagej.net/Fiji/Downloads) (tested with Fiji including ImageJ version 1.51r).

### Installation instructions
- The ImageJ plugins are directly downloaded from the release pages of the individual repositories (download the newest releases of [CiliaQ_Preparator](https://github.com/hansenjn/CiliaQ_Preparator/releases), [CiliaQ_Editor](https://github.com/hansenjn/CiliaQ_Editor/releases), and [CiliaQ](https://github.com/hansenjn/CiliaQ/releases). The plugins are installed by drag and drop into the ImageJ window (after opening ImageJ) and confirming the installation by pressing save. Next, ImageJ requires to be restarted. Typically the installation process of ImageJ plugins takes only few seconds / minutes (the time that your computer needs to launch ImageJ and relaunch it after placing the plugins).
- Some functions (Hysteresis thresholding, Canny3D) in CiliaQ_Preparator require additional installation of the ['3D ImageJ Suite'](https://imagejdocu.tudor.lu/plugin/stacks/3d_ij_suite/start) to your FIJI / ImageJ distribution. To install the 3D ImageJ Suite, download the [core libary](https://imagejdocu.tudor.lu/_media/plugin/stacks/3d_ij_suite/mcib3d-core-3.96.jar) and the [plugin](https://imagejdocu.tudor.lu/_media/plugin/stacks/3d_ij_suite/mcib3d_plugins-3.96.jar) from 3D ImageJ Suite's download section and install them to your FIJI / ImageJ by drag and drop into the FIJI / ImageJ window.

### User Guide / Manual
A User Guide for the whole CiliaQ pipeline is available [here](https://github.com/hansenjn/CiliaQ/blob/master/Webfiles/CiliaQ_SOP.pdf).

**Important Note**: Since last update of the Manual the link to the 3D ImageJ suite has changed (the link in the current Manual is wrong). Please find the "core" and "plugins" file for the 3D suite now at: https://mcib3d.frama.io/3d-suite-imagej/

A Q&A repository in the [CiliaQ wiki](https://github.com/hansenjn/CiliaQ/wiki/Home/) lists answers to common questions.

### How to stay up-to-date?
You want to be always up-to-date about CiliaQ and get news on CiliaQ (new features, updates, etc.)? Watch this repository on GitHub (see Watch button on upper right) or send an email with "Subscribe CiliaQ" to jan.hansen (at) uni-bonn.de.

### Ideas? Missing functions? Issues? Bugs? Unclear in the User Guide?
Are you missing a function or a parameter in CiliaQ?
Is an explanation missing in the User Guide?
Are you encountering problems with CiliaQ?

Please let us know and report this by submitting an issue using the issue tracking systems for the github repositories or sending a message to jan.hansen (at) uni-bonn.de:
- For asking questions or propsing ideas, e-mail to jan.hansen (at) uni-bonn.de or use our discussions page: [CiliaQ Discussions](https://github.com/hansenjn/CiliaQ/discussions)
- For general issues, CiliaQ, and User Guide: [CiliaQ issue system](https://github.com/hansenjn/CiliaQ/issues)
- For issues concerning CiliaQ_Preparator only: [CiliaQ_Preparator issue system](https://github.com/hansenjn/CiliaQ_Preparator/issues)
- For issues concerning CiliaQ_Editor only: [CiliaQ_Editor issue system](https://github.com/hansenjn/CiliaQ_Editor/issues)

## Source code
The source code for the individual ImageJ plugins and java tools is available at the respective repositories:
- [CiliaQ_Preparator](https://github.com/hansenjn/CiliaQ_Preparator)
- [CiliaQ_Editor](https://github.com/hansenjn/CiliaQ_Editor)
- [CiliaQ](https://github.com/hansenjn/CiliaQ)

## Publications presenting an analysis based on CiliaQ
- Hansen, J.N., Kaiser, F., et al. **2020**. Nanobody-directed targeting of optogenetic tools to study signaling in the primary cilium. *eLife* 9:e57907. https://doi.org/10.7554/eLife.57907
- Arveseth, C.D., Happ, J.T., et al. **2021**. Smoothened transduces hedgehog signals via activity-dependent sequestration of PKA catalytic subunits. *PLOS Biology* 19(4): e3001191. https://doi.org/10.1371/journal.pbio.3001191
- Sun, J., Shin, D.Y., Eiseman, M. et al. **2021**. SLITRK5 is a negative regulator of hedgehog signaling in osteoblasts. *Nat Commun* 12, 4611. https://dx.doi.org/10.1038/s41467-021-24819-w
- Ancel, J., Belgacemi, R., Diabasana, Z., et al. **2021**. Impaired Ciliary Beat Frequency and Ciliogenesis Alteration during Airway Epithelial Cell Differentiation in COPD. *Diagnostics* 11(9):1579. https://doi.org/10.3390/diagnostics11091579
- D’Gama, P.P., et al. **2021**. Diversity and function of motile ciliated cell types within ependymal lineages of the zebrafish brain. *Cell Rep.* 37. https://dx.doi.org/10.1016/j.celrep.2021.109775.

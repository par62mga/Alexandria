# Udacity Super Duo Project - Alexandria

## Background
In this project, you will productionize two apps, taking them from a functional state to a production-ready state. To do this, you will find and handle error cases, add accessibility features, allow for localization, add widgets, and add a library.

## Core Components
- Alexandria has bar code scanning functionality: **implemented with ZBar library**
- Alexandria does not crash while searching for a book without an internet connection: **fixed various issues in BookService and other null pointer crashes**

## Optional Components
- Alexandria’s bar code scanning functionality does not require the installation of a separate app on first use: **ZBar library is compiled with the application**
- Strings are all included in the strings.xml file and untranslatable strings have a translatable tag marked to false: **implemented**
- Extra error cases are found, accounted for, and called out in code comments: **see MainActivity.java for details**

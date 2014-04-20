## PinDrop Example Project

An example project that demonstrates the use of low-speed Live Cards using RemoteViews. This was originally developed for Glass NYC's Intro to GDK class, March 2014.

Jenny "Mimming" Murphy will forever disagree with me, but I prefer low-speed to high-speed Live Cards because they're easier to set up and don't require a looping thread to maintain. Since all the official platform samples are high speed, consider this a counterpart.

The map tile is downloaded from the Google Static Maps API.

This project was based off the Timer platform sample and you might see the occasional remnant here and there. It inherits the sample's Apache license.

This project is formatted for Eclipse and depends on GDK Preview (XE16).

# Topics included:
- Inflating RemoteViews and assigning them to Live Cards (low-speed rendering)
- Changing the content of RemoteViews at runtime
- Special GDK API's like voice triggers, sounds, and the Timeline
- Modifying a Live Card's Menu at runtime, assigning Intents, binding it to the base Service, and stopping the Glassware
- Standard Android features like asynchronous tasks, downloading, and LocationManager

# Instructions for use:
1. Load onto Glass by deploying with an IDE or sideloading the APK from this repo's /bin/ folder
2. Activate with "ok glass, drop a pin"
3. Wait for location and for the map tile to load
4. Tap the Live Card to get directions to the dropped pin or to quit the Glassware

# Learn by doing:
- Integrate the Mirror API to save Pins to the Timeline
- Allow the user to zoom the map in and out
- Change the "Loading" front-end to an Immersion (Activity)
- Add a CardStackView that shows the last few pins the user dropped, and store the data in internal storage
- Acquire the user's credentials from the AccountManager and save Pins to their Google Maps
- Experiment with different Location Criteria while paired to different devices
- Give the user a compass that points back to the Pin
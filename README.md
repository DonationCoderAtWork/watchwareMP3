# WatchWare MP3

A full-featured MP3 player app designed specifically for Android based / Wear OS smartwatches.

## Features

- Browse and play audio files directly from your Wear OS device
- Watch-optimized Material Design interface
- Background playback
- Bluetooth headphone controls support
- Album art

## Screenshots

Here's a look at WatchWare MP3 in action:

### Media Browser Screen
![Media Browser Screen](doc/Screenshot1.png)

The media browser allows you to navigate through your audio files and folders directly on your watch. 

### Player Interface
![Player Interface](doc/Screenshot2.png)

The player screen features:
- Album artwork that dynamically influences the UI color theme
- Touch-friendly playback controls (play/pause, next, previous)
- Progress slider for easy navigation within tracks
- Automatic fading of controls after period of inactivity for distraction-free viewing

### Advanced Playback Controls
![Advanced Playback Controls](doc/Screenshot3.png)

Additional features include:
- Volume control with mute function
- Music visualization options
- Optimized for small screen visibility while maintaining full functionality
- Support for playlist navigation

## Requirements

- Wear OS 3.0 or higher
- Android 9.0+ on the connected phone (for file transfer)
- Minimum of 30MB free storage on your watch
- For optimal experience: Wear OS device with at least 1GB RAM

## Installation

To install WatchWare MP3 on your Wear OS device:

1. Download the latest APK from the [Releases](https://github.com/your-username/watchwareMP3/releases) page
2. Enable developer mode on your watch (Settings > System > About > tap Build number 7 times)
3. Enable ADB debugging on the watch
4. Install using ADB: `adb install -r watchwareMP3.apk`

Alternatively, you can build from source:

1. Clone this repository
2. Open the project in Android Studio
3. Build and deploy to your connected Wear OS device

### Transferring Music Files

To add music to your watch:

1. Connect your watch to your computer via USB
2. Enable file transfer mode on your watch
3. Copy MP3 files to the `Music` folder on your watch
4. Safely disconnect and launch the app

## Support and Donations

If you find WatchWare MP3 useful, please consider supporting its development:

1. **Spread the word** - Tell other Wear OS users about this app
2. **Provide feedback** - Open issues on GitHub for bugs or feature requests
3. **Contribute code** - Pull requests are welcome
4. **Donate** - Your financial support helps cover development costs and enables new features

Donations directly influence which features get prioritized. Even small contributions make a big difference!

## Contributing

Contributions are welcome! If you'd like to help improve WatchWare MP3:

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Frequently Asked Questions

**Q: Why does the app need storage permissions?**  
A: The app needs access to your device's storage to find and play audio files stored on your watch.

**Q: Will this app drain my watch battery?**  
A: WatchWare MP3 is optimized for Wear OS devices, but playing music will use battery. For extended listening, consider connecting to power.

**Q: Can I control playback from my phone?**  
A: Currently, the app is designed to work independently on your watch. Phone-based controls may be added in future versions.

**Q: Does this work with all Wear OS watches?**  
A: The app should work on most Wear OS devices running version 3.0 or higher with sufficient storage space.

# watchware by DonationCoderAtWork - Apps for your Android based Smartwatch
### Moving from smartphone to smartwatch

**What?** - A growing collection of practical, standalone apps built specifically for Android-based smartwatches like Galaxy Watch, Pixel Watch, Xiaomi Watch — and possibly Huawei.\
**Why?** - Use your watch more for whats important and your phone less for what in the end is just stealing time.\
**How?** - Download -> Install -> Use -> Donate back a part of the value you received.\
**Where?** - I choose to release on GitHub for flexibility, freedom, and faster iteration. App store submissions take time and limit what I can build for the people who care.\
**Motivation?** - To fill the clear gap in smartwatch-first apps — designed for utility, focus, and simplicity. I build what matters to me and to the supporters who make this work possible.\
**Code Quality?** - The apps are functional, privacy-respecting, and intentionally lightweight. If it works, adds value, and protects users, it meets the mark — even if the code isn’t perfect.\
**Priorities?** - I focus my time and energy where it’s appreciated: on features, ideas, and feedback from users who sponsor or contribute. This keeps development joyful and sustainable.\
**Where do donations go?** - Your support directly funds development time and tools. The more support I receive, the more energy I can put into improving these apps and creating new ones.\
**Something missing?** - Alot! Of course! - just getting started\

Feel free to get in touch via email (noted in a cryptic way to prevent spam).\
Send it to my email provider yahoo using the international domain .com with the name d34g13 in front of the '@'.

## Contact

Feel free to get in touch via email (noted in a cryptic way to prevent spam).\
Send it to my email provider yahoo using the international domain .com with the name d34g13 in front of the '@'.

## License

This project is licensed under the [Creative Commons Attribution-NonCommercial 4.0 International License](LICENSE.md).

This means you are free to:
- Share — copy and redistribute the material in any medium or format
- Adapt — remix, transform, and build upon the material

Under the following terms:
- Attribution — You must give appropriate credit and indicate if changes were made
- NonCommercial — You may not use the material for commercial purposes

See the [LICENSE.md](LICENSE.md) file for full details.

## Release Notes

For a detailed list of changes and version history, please see the [CHANGELOG.md](CHANGELOG) file.

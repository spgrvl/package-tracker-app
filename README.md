# Greek Package Tracker 

<div align="center">
  <a href='https://play.google.com/store/apps/details?id=com.spgrvl.packagetracker'>
    <img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png' height="80px"/>
  </a>
</div>

<div align="center">
  <strong>📦 Track all your Greek packages in one place! 🇬🇷</strong>
</div>

---

## About

Greek Package Tracker is an Android application designed to help users track their packages from multiple Greek carriers in a single, unified interface. No more switching between different carrier websites or apps - track everything from one convenient location and get notified for any updates!

## Features

- **Multi-Carrier Support**: Track packages from 11+ major Greek carriers
- **Barcode Scanning**: Quickly add packages by scanning tracking codes
- **Auto-Refresh**: Automatic background updates for package status
- **Notifications**: Get notified when package status changes
- **Package History**: Keep track of completed deliveries
- **Modern UI**: Clean, intuitive interface with Material Design
- **Dark Mode**: Full dark theme support
- **Multi-Language**: Support for Greek and English

## Supported Carriers

| Carrier | Website |
|---------|---------|
| **ELTA** | [elta.gr](https://www.elta.gr/) |
| **ELTA Courier** | [elta-courier.gr](https://www.elta-courier.gr/) |
| **ACS Courier** | [acscourier.net](https://www.acscourier.net/) |
| **Geniki Taxydromiki** | [taxydromiki.com](https://www.taxydromiki.com/) |
| **Speedex** | [speedex.gr](https://www.speedex.gr/) |
| **BoxNow** | [boxnow.gr](https://boxnow.gr/) |
| **Skroutz Last Mile** | [skroutzlastmile.gr](https://www.skroutzlastmile.gr/) |
| **Comet Hellas** | [comethellas.gr](https://www.comethellas.gr/) |
| **Courier Center** | [courier.gr](https://www.courier.gr/) |
| **Delatolas Courier** | [delatolascourier.gr](https://www.delatolascourier.gr/) |
| **Easy Mail** | [easymail.gr](https://www.easymail.gr/) |

## Technical Details

- **Platform**: Android (API 24+)
- **Language**: Java
- **Architecture**: Native Android with SQLite database
- **Minimum Android Version**: 7.0 (API 24)
- **Target SDK**: 35

## Getting Started

### Installation

1. **From Google Play Store** (Recommended):
   - Download from the [Google Play Store](https://play.google.com/store/apps/details?id=com.spgrvl.packagetracker)

2. **Build from Source**:
   ```bash
   git clone https://github.com/spgrvl/package-tracker-app.git
   cd package-tracker-app
   ./gradlew assembleDebug
   ```

### Usage

1. **Add a Package**: 
   - Tap the + button to manually enter a tracking number
   - Or use the camera icon to scan a barcode
   
2. **Track Progress**: 
   - View real-time updates on the main screen
   - Pull down to refresh manually
   - Tap any package for detailed tracking history
   
3. **Manage Packages**: 
   - Access delivered packages history via the menu > Completed
   - Long press to bulk select packages
   - Edit, delete, copy details or open package carrier's website via the menu 

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

### Development Setup

1. Clone the repository
2. Open in Android Studio
3. Sync project with Gradle files
4. Run on device or emulator

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
# Beam


*Real-time battery monitor for Android.*

[<img src="https://github.com/ImranR98/Obtainium/blob/main/assets/graphics/badge_obtainium.png?raw=true" alt="Get it on Obtainium" height="60">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/montafra/beam)

Beam displays live battery metrics as a persistent status bar notification and shows a full breakdown in-app. You can choose which additional metrics appear in the notification body.

Beam is inspired by the original [Wattz](https://github.com/dubrowgn/wattz) app.

## Metrics

- Power (watts)
- Current (amps)
- Voltage (volts)
- Energy level (watt-hours and amp-hours)
- Temperature (celsius)
- Charge level (percent)
- Is charging (yes/no)
- Charging since (date/time)
- Time to full charge (duration)

## Privacy

- No unnecessary permissions
- No ads
- No collection of user data of any kind
- No sharing data with third parties

## Screenshots

<img src="readme/00.oled.png" alt="Home (OLED)" width="48%" /> <img src="readme/03.notification.png" alt="Notification" width="48%" />

<img src="readme/01.header.png" alt="Header" width="48%" /> <img src="readme/02.theme.png" alt="Theme settings" width="48%" />

## FAQ

**Why does my phone always show `0W`?**

Many phones, especially Samsungs, don't follow the BatteryManager spec. Try changing "Power Scalar Workaround" in the settings view.

**Why does my external power meter show different numbers than Beam?**

Beam can only measure power flowing into or out of the battery management system. An external meter also captures power the phone draws on top of that.

**Why isn't the indicator showing up in my status bar?**

Beam needs notification permissions on newer Android phones. Open the app once and grant permissions when prompted, or enable them manually in Android app settings. Note that Android can silently revoke these permissions, so you may need to re-enable them periodically.

## Support Me

**Bitcoin (BTC):** 
```
bc1q7v38g2xn7wxtwn6ewde4kydn5emjr3zt73ew96
```
**Monero (XMR):**
```
876wwukGWhU9H6qez4Qmt5gTBBmdKzoDg3zvT33QCwjy9e7jS7MVjQySUCpNhoVrFcF15AicUJ4VaVrTKAXGMu5D7yUbqFs
```
**Lighting:**
```
monta@cake.cash
```

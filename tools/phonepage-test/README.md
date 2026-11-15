# Phone page test

Loads the phone remote's web page (extracted from `PhoneRemote.kt`) in jsdom and checks the link-sharing flow: the
confirmation card, the fragment handling, refusal of non-web schemes, pairing from the QR fragment and the bookmarklet.

```bash
cd tools/phonepage-test
npm install
npm test
```

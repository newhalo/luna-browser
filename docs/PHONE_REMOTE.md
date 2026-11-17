# Phone remote

Typing with a TV remote is slow, and not every remote has a Menu key. With *Settings → Phone remote* on (the default),
Luna listens on your home network while it is on screen:

1. On the TV open **Settings → Phone remote**: it shows a QR code, the address (such as `http://192.168.1.20:8765`)
   and a 6-digit PIN.
2. On a phone connected to the same Wi-Fi, scan the QR code with the camera: the page opens already paired (or open the
   address by hand and enter the PIN once). The phone remembers the pairing.
3. The **Remote** tab is a full remote in your hand: D-pad (hold an arrow to keep the pointer moving), **OK**, Back,
   Home, Menu, rewind / play-pause / fast-forward and volume. The **Type** tab sends text: type an address or words and
   tap **Open on TV** or **Search on TV**; to fill a text box on a page (a site's search box, a login) put the pointer
   on the box, press OK, then tap **Type into the page** (or **Type and press Enter**).

**Share a page from your phone.** On the **Type** tab, *Copy bookmarklet* gives you a one-line bookmark. Save it in your
phone's browser; opening it on any page brings you back to this page with that address ready, and one tap on *Open on TV*
sends it. (A real Android *Share* button would need HTTPS, which a TV on a home network can't have.) The address is never
sent until you tap.

Several phones can be paired and used at the same time; they share the PIN, and wrong guesses are counted per phone,
so one stale page can't lock the others out.

**Safety.** Every request needs the PIN; five wrong PINs lock that phone for 30 seconds; it only opens web pages,
searches, presses the remote keys inside Luna, changes the volume and types into the focused field; and it stops
listening when Luna is not on screen. The traffic is plain HTTP on your local network, so turn it off (the same
settings page) on networks you don't trust. *New PIN* unpairs all phones. Details: [SECURITY.md](../SECURITY.md).

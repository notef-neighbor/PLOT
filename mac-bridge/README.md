# PLOT Mac History Bridge

This companion keeps raw Mac Computer History events on the Mac. It exposes only the generated 10-minute and 6-hour summaries to a paired PLOT Android device over authenticated TLS.

```bash
cd mac-bridge
npm run install-service
tail -f ~/.plot-history-bridge/bridge.log
```

Paste the printed `PLOT-MAC-1:...` pairing code into PLOT under **Settings → Mac Computer History**. Credentials and the self-signed TLS certificate are created in `~/.plot-history-bridge` with owner-only permissions.

The bridge listens on the local network by default. Keep macOS Firewall enabled and pair only devices you control. Set `PLOT_MAC_BRIDGE_HOST=127.0.0.1` to make it local-only.

The launch agent keeps the bridge available in the background after Mac sign-in. To stop automatic startup, run `npm run uninstall-service`. Already imported Android history is not deleted.

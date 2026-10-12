# Narto Drama for CloudStream

A CloudStream plugin project for browsing Narto Drama's public drama catalogue.

## Features in this starter

- Searches Narto Drama using its public search page.
- Lists popular titles from the public home page.
- Reads drama details and episode links when they are present in the page.
- Attempts to hand public HTML5 video sources or embeds to CloudStream's normal player/extractors.

**Playback is not yet verified.** Narto Drama appears to use dynamically loaded playback, so the provider may need adjustments after the first real build/test. The extension will not invent stream URLs or bypass login, paid access, DRM, or access restrictions.

## Build it (no PC required)

1. Open the **Actions** tab in this repository.
2. Select **Build NartoDrama CloudStream plugin**.
3. Tap **Run workflow** (or push a commit to `main` to trigger it automatically).
4. Open the finished run and download the `NartoDrama-cs3-plugin` artifact.
5. Extract the ZIP and install the `.cs3` file in CloudStream if your CloudStream version supports local plugin installation.

The workflow builds the extension using the upstream CloudStream plugin template. The first build can fail if that template or the upstream Gradle plugin changes; check the action log if it does.

## Sources

- Narto Drama: https://nartodrama.com/
- CloudStream: https://github.com/recloudstream/cloudstream
- Plugin template: https://github.com/cloudstream3/plugin-template

## Safety and privacy

Use only public pages and playback sources you are authorized to access. Never commit passwords, cookies, session tokens, or personal account data.

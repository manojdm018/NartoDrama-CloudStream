# Narto Drama + DramaFren for CloudStream

A CloudStream plugin with two independent providers: Narto Drama and DramaFren. Each provider keeps its own search results, series pages, and episode links.

## Features in this starter

- Searches and browses Narto Drama's public catalogue.
- Searches and browses DramaFren's public series pages as a separate provider.
- Reads available series details and episode links from each site's public HTML.
- Attempts to hand public HTML5 video sources or embeds to CloudStream's normal player/extractors.

**Playback is not yet verified.** Narto Drama appears to use dynamically loaded playback, so the provider may need adjustments after the first real build/test. The extension will not invent stream URLs or bypass login, paid access, DRM, or access restrictions.

## Build it (no PC required)

1. Open the **Actions** tab in this repository.
2. Select **Build NartoDrama and DramaFren CloudStream plugin**.
3. Tap **Run workflow** (or push a commit to `main` to trigger it automatically).
4. Open the finished run and download the `NartoDrama-DramaFren-cs3-plugin` artifact.
5. Extract the ZIP and install the `.cs3` file in CloudStream if your CloudStream version supports local plugin installation.

The workflow builds the extension using the CloudStream plugin tools. It also publishes a `builds` branch intended to work as a CloudStream repository.

## Install from CloudStream

After the latest **Build NartoDrama CloudStream plugin** workflow finishes successfully:

1. Open CloudStream and go to **Extensions**.
2. Find the option to add an extension repository.
3. Paste this repository manifest URL:

   `https://raw.githubusercontent.com/manojdm018/NartoDrama-CloudStream/builds/repo.json`

4. Refresh repositories and install the extension and choose **Narto Drama** or **DramaFren** as separate providers.

If the repository manifest is not available yet, open the **Actions** tab and check the latest build. You can also download the `NartoDrama-cs3-plugin` artifact from a successful run and extract the `.cs3` file instead.

**Important:** A successful build only confirms the plugin compiles. Searching, episode listing, and especially video playback must still be tested against the live Narto Drama website; its player may use dynamically loaded streams that this initial version cannot see.

## Sources

- Narto Drama: https://nartodrama.com/
- CloudStream: https://github.com/recloudstream/cloudstream
- Plugin template: https://github.com/cloudstream3/plugin-template

## Safety and privacy

Use only public pages and playback sources you are authorized to access. Never commit passwords, cookies, session tokens, or personal account data.

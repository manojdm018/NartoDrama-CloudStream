# NartoDrama and DramaFren — separate CloudStream extensions

This repository builds **two independent CloudStream plugins**. They are not bundled into one extension:

- **NartoDrama** — its own `.cs3` package and repository.
- **DramaFren** — its own `.cs3` package and repository, using https://dramafren.org/.

Each plugin has its own provider registration, search, catalogue, series details, and episode links. DramaFren uses standard CloudStream extractors for public player embeds. Playback still needs testing on a real CloudStream installation because embedded servers can change.

## Install NartoDrama

Add this repository in CloudStream under **Extensions → Add repository**:

```
https://raw.githubusercontent.com/manojdm018/NartoDrama-CloudStream/builds/repo.json
```

## Install DramaFren separately

Add this second repository in CloudStream:

```
https://raw.githubusercontent.com/manojdm018/NartoDrama-CloudStream/dramafren-builds/repo.json
```

The two URLs list different plugins. Installing DramaFren does not install NartoDrama, and installing NartoDrama does not install DramaFren.

## Build

GitHub Actions builds both modules separately and publishes their individual packages. Pull-request builds compile the packages but do not publish either repository branch. The build artifact contains both `.cs3` files for manual installation if needed.

## Content preferences and language versions

Both providers filter explicit BL and common female-lead metadata signals. NartoDrama filters dubbed releases from the visible catalogue and, where it finds an exact-title counterpart, links the dubbed release to the original entry. The detail page can present matched episodes as `Episode N · SUB` and `Episode N · DUB` choices in the same episode list. This is the plugin-only approach; the standard CloudStream plugin interface does not expose an arbitrary custom segmented SUB/DUB toggle inside the detail page.

Pairing deliberately requires an exact normalized title match and a valid detail page. A paired choice may be unavailable when the source has no counterpart, uses inconsistent naming, or supplies incomplete metadata. Original audio, dubbed audio, and subtitle availability are distinct properties, and subtitles still depend on the source providing subtitle tracks.

The provider narrows a few overly broad role keywords that could incorrectly exclude a male-led drama just because its metadata mentions a princess, queen, or similar character. Metadata-based filtering is conservative: titles with insufficient or misleading source metadata cannot be classified perfectly.

## Sources

- Narto Drama: https://nartodrama.com/
- DramaFren: https://dramafren.org/
- CloudStream: https://github.com/recloudstream/cloudstream
- CloudStream extensions template: https://github.com/recloudstream/extensions

Use only public pages and playback sources you are authorized to access. Do not commit cookies, tokens, or personal account data.

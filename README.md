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

## Content preferences

Both providers filter explicit BL and common female-lead metadata signals. This is a metadata-based filter, so a title may still need manual review if the site does not publish reliable genre or lead information. Custom in-detail SUB/DUB toggles are not supported by the standard CloudStream plugin interface; a paired version must also exist on the source site.

## Sources

- Narto Drama: https://nartodrama.com/
- DramaFren: https://dramafren.org/
- CloudStream: https://github.com/recloudstream/cloudstream
- CloudStream extensions template: https://github.com/recloudstream/extensions

Use only public pages and playback sources you are authorized to access. Do not commit cookies, tokens, or personal account data.

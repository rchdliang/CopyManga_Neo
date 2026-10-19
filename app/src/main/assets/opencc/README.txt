OpenCC dictionaries for metadata display
Source: https://github.com/BYVoid/OpenCC
Copyright OpenCC contributors. Apache License 2.0 (see LICENSE).
Dictionary data is downloaded from the latest stable official release only when
simplification is enabled or the user selects update. Files from the same commit
are verified against GitHub Git blob hashes, then stored atomically with SHA-256
receipts for offline validation. No dictionary data is bundled in the application.
Test fixtures include original upstream dictionaries from ver.1.1.9 and ver.1.4.2;
they are excluded from application packages.
Conversion follows tw2s segmentation, compatible-character normalization and
Taiwan variant / Traditional-to-Simplified stages without regional-word rewriting.

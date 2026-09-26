# CLAUDE.md — docs/

## Purpose

Holds the behavioural contract shared by the Windows and Android apps. This directory is
specification, not documentation of code that already explains itself.

## Ownership

- `download-rules.md` — every rule that determines *what gets downloaded and how it is
  encoded*: format selectors, passthrough matrix, bitrate ladder, title cleanup, metadata
  mapping, queue construction, error text, output naming.

## Local Contracts

- `download-rules.md` is authoritative. When it and an implementation disagree, the
  implementation is wrong — unless the difference is listed in the "Platform divergences"
  table, which is the only sanctioned way for the two apps to differ.
- Rules are stated as pure functions of user selections plus probed source info, so both
  implementations can be tested against them directly.
- Line references into `src/Program.cs` are provenance, not API. They will drift; the
  prose is what binds.
- Mechanism belongs here only when it is observable. Output *filenames* are contract;
  the directory-scanning used to produce them is not.

## Work Guidance

- Change this file before changing either implementation, never after.
- When a rule in the Windows reference is wrong rather than merely quirky, document the
  quirk, state the corrected behaviour, and add a row to "Platform divergences". Do not
  silently port a defect, and do not silently fix one.

## Verification

Android's `core/` unit tests assert against this file. A rule change that the tests do
not cover is not finished.

## Child DOX Index

None.

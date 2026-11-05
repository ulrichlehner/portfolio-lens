# Third-party software in the release archive

The runtime libraries in `lib/` of a release archive. The release workflow fails when a jar in
`target/lib/` is not named here, so a new or updated dependency must be listed in the same pull
request.

| File | Project | License |
| :--- | :--- | :--- |
| `gson-2.11.0.jar` | Gson, https://github.com/google/gson | Apache License 2.0 |
| `error_prone_annotations-2.27.0.jar` | Error Prone annotations (dependency of Gson), https://github.com/google/error-prone | Apache License 2.0 |

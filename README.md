# Portfolio Lens

Look-through analysis for [Portfolio Performance](https://github.com/portfolio-performance/portfolio):
decompose the funds of a portfolio into their companies and see the combined exposure by company,
sector, country and region.

Portfolio Lens runs next to Portfolio Performance and reads the open portfolio through its local
REST API. It never changes the portfolio.

**Status:** early development, not usable yet. The plan is in [`openspec/`](openspec/).

## Notice

Portfolio Lens is an independent project. It is not part of Portfolio Performance and is not
supported by the Portfolio Performance maintainers. It is not affiliated with any data provider.
Fund and company data comes from providers using your own account. Provider permissions and data
completeness are not verified: make sure your plan allows your use, including local caching. Use at
your own risk.

## Development

Requirements on the host: git and Docker. No Java or Maven.

```bash
scripts/dev.sh            # list of commands
scripts/openspec list     # OpenSpec changes
```

Or open the folder in VS Code and choose "Dev Containers: Reopen in Container". The rules for
contributors and AI agents are in [AGENTS.md](AGENTS.md).

## License

[Apache License 2.0](LICENSE)

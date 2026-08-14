# Releasing

The renderer and integration packages are versioned together with Changesets fixed mode: `@liquid-dom/core`, `@liquid-dom/react`, `@liquid-dom/three`, and `@liquid-dom/r3f`. If any package in that group needs a new version, every package in the group receives the same version.

`@liquid-dom/layout` is versioned independently because it can be used on its own.

## First publish

```sh
npm login
pnpm release:check
pnpm release:publish
```

The first publish uses the `0.1.0` versions already recorded in the package manifests.

## Future releases

```sh
pnpm changeset
pnpm release:version
pnpm install
pnpm release:check
pnpm release:publish
```

Choose the release type based on the public API change. Changesets will keep the renderer and integration package versions aligned while allowing `@liquid-dom/layout` to release independently.

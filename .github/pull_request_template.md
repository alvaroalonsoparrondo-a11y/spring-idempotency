## Summary

<!-- What does this PR change and why? The title follows the commit style: type(lowercase summary). -->

## Related issues

<!-- Fixes #123, Relates to #456 -->

## Type of change

- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (fix or feature that would cause existing behavior to change)
- [ ] Documentation update
- [ ] Refactoring / chore

## Affected modules

- [ ] `ride-service`
- [ ] `receipt-service`
- [ ] `rides-contract`
- [ ] `ride-simulator` / `load-tests`
- [ ] end-to-end tests
- [ ] `docker-compose` / infrastructure
- [ ] docs / CI / other

## Test plan

- [ ] `./mvnw verify`
- [ ] Manual testing (describe below)

**Manual steps (if any):**

```bash
# e.g. docker compose --profile apps up -d --build --wait, then the requests in requests.http
```

## Checklist

- [ ] Code follows existing project conventions
- [ ] Tests added or updated where appropriate
- [ ] Documentation updated (if user-facing behavior changed)
- [ ] No secrets or environment-specific credentials committed
- [ ] Schema changes are new migrations (released ones are never edited)
- [ ] Codecov patch coverage check is green (or explained in the description)
- [ ] `CHANGELOG.md` is not edited (the maintainer writes it at release time)

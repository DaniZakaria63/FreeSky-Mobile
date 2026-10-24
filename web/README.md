# web/ — public Freesky policy pages

Static copies of the pages served at:

- https://www.dream-team.works/freesky/privacy-policy — from `PRIVACY_POLICY.md`
- https://www.dream-team.works/freesky/safety-standards — from `SAFETY_STANDARDS.md`

The HTML files are the deployed artifacts; there is no build step. When the
markdown sources change, update the matching `index.html` by hand and redeploy
(keep content in sync).

## Where they are hosted

On the dream-team.works nginx server as
`/var/www/dream-team.works/freesky/<page>/index.html` (owner `deploy:deploy`,
mode 644). Cloudflare terminates TLS and tunnels to nginx; the
`dream-team.works` vhost (config source: `portofolio` repo,
`server/nginx/dream-team.works.conf`) serves these paths with the shared
portfolio security headers and `must-revalidate` caching, at 200 for both
`/page` and `/page/`.

## Deploy

From this directory (needs SSH access to `ubuntu@admin.dream-team.works`):

```sh
scp -r privacy-policy safety-standards ubuntu@admin.dream-team.works:/tmp/freesky-web/
ssh ubuntu@admin.dream-team.works '
  sudo install -d -o deploy -g deploy -m 755 /var/www/dream-team.works/freesky/privacy-policy /var/www/dream-team.works/freesky/safety-standards
  sudo install -o deploy -g deploy -m 644 /tmp/freesky-web/privacy-policy/index.html /var/www/dream-team.works/freesky/privacy-policy/index.html
  sudo install -o deploy -g deploy -m 644 /tmp/freesky-web/safety-standards/index.html /var/www/dream-team.works/freesky/safety-standards/index.html
'
```

Verify:

```sh
curl -sI https://www.dream-team.works/freesky/privacy-policy
curl -sI https://www.dream-team.works/freesky/safety-standards
```

## App-side URL

The app shows the policy URL from Firebase Remote Config key
`privacy_policy_url`, falling back to `DEFAULT_PRIVACY_URL` in
`network/.../RemoteConfigManager.kt` (both defaults now point at the URL
above). If the key is also set in the Firebase console, update it there too.

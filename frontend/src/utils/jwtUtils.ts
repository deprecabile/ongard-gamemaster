export function parseJwtUsername(token: string): string {
  const parts = token.split('.');
  const encoded = parts[1] ?? '';
  const payload = JSON.parse(atob(encoded)) as { username: string };
  return payload.username;
}

UPDATE playlists
SET invite_code = translate(
    substr(md5(concat(id, clock_timestamp()::text, random()::text)), 1, 8),
    '0123456789abcdef',
    'ABCDEFGHIJKLMNOP'
);

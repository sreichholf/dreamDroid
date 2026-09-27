-- One profile with every 1.15 column set away from its default.
-- __TABLE__ is `profile` (1.15 Room v1, `dreambox`) or `profiles`
-- (pre-Room SQLite v14, `dreamdroid`); both use these column names.
-- Upgrade115Test asserts these values after the 2.0 install.
INSERT INTO __TABLE__ (
    _id, profile, host, streamhost, port, streamport, fileport,
    login, user, pass, ssl, trust_all_certs,
    streamlogin, file_login, file_ssl, simpleremote,
    default_ref, default_ref_name, default_ref_2, default_ref_2_name,
    encoder_stream, encoder_path, encoder_port, encoder_login,
    encoder_user, encoder_pass, encoder_video_bitrate, encoder_audio_bitrate,
    ssid, defaultProfileOnNoWifi
) VALUES (
    42, 'Upgrade Box', '10.11.12.13', '10.11.12.14', 8443, 8002, 8080,
    1, 'upuser', 'uppass', 1, 1,
    1, 1, 1, 1,
    '1:7:1:0:0:0:0:0:0:0:FROM BOUQUET "userbouquet.favourites.tv" ORDER BY bouquet',
    'Favourites',
    '1:7:1:0:0:0:0:0:0:0:FROM BOUQUET "bouquets.tv" ORDER BY bouquet',
    'Bouquets',
    1, 'transcode', 5554, 1,
    'encuser', 'encpass', 4000, 192,
    'UpgradeWifi', 1
);

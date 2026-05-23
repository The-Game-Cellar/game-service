-- Seed curation flags + categories on the platforms table.
-- 79 platforms flagged as preference-eligible, grouped by manufacturer for the Preferences picker.
-- All other rows in `platforms` remain is_preference_eligible = FALSE (their default) — still attach to games,
-- still visible on Add-to-Library / Library filter / Game Detail metadata, just hidden from the Preferences UI.

UPDATE platforms p
SET is_preference_eligible = TRUE,
    category               = v.category,
    display_order          = v.display_order
FROM (VALUES
    -- sony
    ('PlayStation 5',                          'sony',      10),
    ('PlayStation 4',                          'sony',      20),
    ('PlayStation 3',                          'sony',      30),
    ('PlayStation 2',                          'sony',      40),
    ('PlayStation',                            'sony',      50),
    ('PlayStation Portable',                   'sony',      60),
    ('PlayStation Vita',                       'sony',      70),
    ('PlayStation VR2',                        'sony',      80),
    ('PlayStation VR',                         'sony',      90),

    -- microsoft
    ('Xbox Series X|S',                        'microsoft', 10),
    ('Xbox One',                               'microsoft', 20),
    ('Xbox 360',                               'microsoft', 30),
    ('Xbox',                                   'microsoft', 40),

    -- nintendo
    ('Nintendo Switch 2',                      'nintendo',   5),
    ('Nintendo Switch',                        'nintendo',  10),
    ('Wii U',                                  'nintendo',  20),
    ('Wii',                                    'nintendo',  30),
    ('Nintendo GameCube',                      'nintendo',  40),
    ('Nintendo 64',                            'nintendo',  50),
    ('Super Nintendo Entertainment System',    'nintendo',  60),
    ('Nintendo Entertainment System',          'nintendo',  70),
    ('Nintendo 3DS',                           'nintendo',  80),
    ('Nintendo DSi',                           'nintendo',  85),
    ('Nintendo DS',                            'nintendo',  90),
    ('Game Boy Advance',                       'nintendo', 100),
    ('Game Boy Color',                         'nintendo', 110),
    ('Game Boy',                               'nintendo', 120),
    ('Virtual Boy',                            'nintendo', 130),

    -- pc
    ('PC',                                     'pc',        10),
    ('Mac',                                    'pc',        20),
    ('Linux',                                  'pc',        30),
    ('DOS',                                    'pc',        40),

    -- mobile
    ('iOS',                                    'mobile',    10),
    ('Android',                                'mobile',    20),

    -- other / sega
    ('Dreamcast',                              'other',     10),
    ('Sega Saturn',                            'other',     20),
    ('Sega Mega Drive/Genesis',                'other',     30),
    ('Sega CD',                                'other',     40),
    ('Sega 32X',                               'other',     50),
    ('Sega Master System/Mark III',            'other',     60),
    ('Sega Game Gear',                         'other',     70),

    -- other / atari
    ('Atari 2600',                             'other',    100),
    ('Atari 5200',                             'other',    110),
    ('Atari 7800',                             'other',    120),
    ('Atari Lynx',                             'other',    130),
    ('Atari Jaguar',                           'other',    140),
    ('Atari ST/STE',                           'other',    150),
    ('Atari 8-bit',                            'other',    160),

    -- other / commodore + amiga
    ('Commodore C64/128/MAX',                  'other',    200),
    ('Amiga',                                  'other',    210),
    ('Amiga CD32',                             'other',    220),
    ('Commodore VIC-20',                       'other',    230),

    -- other / retro home computers
    ('ZX Spectrum',                            'other',    300),
    ('Amstrad CPC',                            'other',    310),
    ('Apple II',                               'other',    320),
    ('MSX',                                    'other',    330),
    ('MSX2',                                   'other',    340),

    -- other / snk neo geo
    ('Neo Geo AES',                            'other',    400),
    ('Neo Geo CD',                             'other',    410),
    ('Neo Geo Pocket Color',                   'other',    420),
    ('Neo Geo Pocket',                         'other',    430),

    -- other / nec turbografx
    ('TurboGrafx-16/PC Engine',                'other',    500),
    ('Turbografx-16/PC Engine CD',             'other',    510),

    -- other / övriga konsumentkonsoler
    ('3DO Interactive Multiplayer',            'other',    600),
    ('ColecoVision',                           'other',    610),
    ('Intellivision',                          'other',    620),
    ('Vectrex',                                'other',    630),
    ('WonderSwan Color',                       'other',    640),
    ('WonderSwan',                             'other',    650),
    ('Playdate',                               'other',    660),
    ('Google Stadia',                          'other',    670),
    ('Ouya',                                   'other',    680),

    -- other / arcade + web
    ('Arcade',                                 'other',    800),
    ('Web browser',                            'other',    850),

    -- other / vr
    ('Meta Quest 3',                           'other',    900),
    ('Meta Quest 2',                           'other',    910),
    ('Oculus Rift',                            'other',    920),
    ('Oculus Quest',                           'other',    930),
    ('Oculus VR',                              'other',    940)
) AS v(name, category, display_order)
WHERE p.name = v.name;

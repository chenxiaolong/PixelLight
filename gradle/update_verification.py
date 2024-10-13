#!/usr/bin/env python3

# SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
# SPDX-License-Identifier: GPL-3.0-only

import hashlib
import io
import os
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET


GOOGLE_MAVEN_REPO = 'https://dl.google.com/android/maven2'


def add_source_exclusions(ns, root):
    configuration = root.find(f'{{{ns}}}configuration')
    trusted_artifacts = ET.SubElement(
        configuration, f'{{{ns}}}trusted-artifacts')

    for regex in [
        r'.*-javadoc[.]jar',
        r'.*-sources[.]jar',
        r'.*-src[.]zip',
    ]:
        ET.SubElement(trusted_artifacts, f'{{{ns}}}trust', attrib={
            'file': regex,
            'regex': 'true',
        })


def add_missing_aapt2_platforms(ns, root):
    components = root.find(f'{{{ns}}}components')
    aapt2 = components.find(f'{{{ns}}}component[@name="aapt2"]')

    for platform in ['linux', 'osx', 'windows']:
        group = aapt2.attrib['group']
        name = aapt2.attrib['name']
        version = aapt2.attrib['version']
        filename = f'{name}-{version}-{platform}.jar'

        if aapt2.find(f'{{{ns}}}artifact[@name="{filename}"]') is not None:
            continue

        path = f'{group.replace(".", "/")}/{name}/{version}/{filename}'
        url = f'{GOOGLE_MAVEN_REPO}/{path}'

        with urllib.request.urlopen(url) as r:
            if r.status != 200:
                raise Exception(f'{url} returned HTTP {r.status}')

            digest = hashlib.file_digest(r, 'sha512')

        artifact = ET.SubElement(aapt2, f'{{{ns}}}artifact',
                                 attrib={'name': filename})

        ET.SubElement(artifact, f'{{{ns}}}sha512', attrib={
            'value': digest.hexdigest(),
            'origin': 'Generated by Gradle',
        })

    aapt2[:] = sorted(aapt2, key=lambda child: child.attrib['name'])


def patch_xml(path):
    tree = ET.parse(path)
    root = tree.getroot()

    ns = 'https://schema.gradle.org/dependency-verification'
    ET.register_namespace('', ns)

    # Add exclusions to allow Android Studio to download sources.
    add_source_exclusions(ns, root)

    # Gradle only adds the aapt2 entry for the host OS. We have to manually add
    # the checksums for the other major desktop OSs.
    add_missing_aapt2_platforms(ns, root)

    # Match gradle's formatting exactly.
    ET.indent(tree, '   ')
    root.tail = '\n'

    with io.BytesIO() as f:
        # etree's xml_declaration=True uses single quotes in the header.
        f.write(b'<?xml version="1.0" encoding="UTF-8"?>\n')
        tree.write(f)
        serialized = f.getvalue().replace(b' />', b'/>')

    with open(path, 'wb') as f:
        f.write(serialized)


def main():
    root_dir = os.path.join(sys.path[0], '..')
    xml_file = os.path.join(sys.path[0], 'verification-metadata.xml')

    try:
        os.remove(xml_file)
    except FileNotFoundError:
        pass

    # Gradle will sometimes fail to add verification entries for artifacts that
    # are already cached.
    with tempfile.TemporaryDirectory() as temp_dir:
        env = os.environ | {'GRADLE_USER_HOME': temp_dir}

        subprocess.check_call(
            [
                './gradlew' + ('.bat' if os.name == 'nt' else ''),
                '--write-verification-metadata', 'sha512',
                '--no-daemon',
                'build',
                'connectedDebugAndroidTest',
                # Requires signing.
                '-x', 'assembleRelease',
            ],
            env=env,
            cwd=root_dir,
        )

    patch_xml(xml_file)


if __name__ == '__main__':
    main()

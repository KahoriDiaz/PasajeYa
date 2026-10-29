import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const APP_ROOT = fileURLToPath(new URL("../../app/src/main", import.meta.url));
const FUNCTIONS_ROOT = fileURLToPath(new URL("../src", import.meta.url));

async function collectFiles(
  root: string,
  extensions: readonly string[]
): Promise<string[]> {
  const entries = await readdir(root, { withFileTypes: true });
  const nested = await Promise.all(
    entries.map(async (entry) => {
      const path = join(root, entry.name);
      if (entry.isDirectory()) return collectFiles(path, extensions);
      return extensions.some((extension) => entry.name.endsWith(extension))
        ? [path]
        : [];
    })
  );
  return nested.flat();
}

function matchingLocations(
  path: string,
  source: string,
  pattern: RegExp,
  predicate: (match: string) => boolean = () => true
): string[] {
  return [...source.matchAll(pattern)]
    .filter((match) => predicate(match[0]))
    .map((match) => {
      const line = source.slice(0, match.index ?? 0).split("\n").length;
      return `${path}:${line}`;
    });
}

describe("F5 privacy static checks", () => {
  it("does not expose the DNI in logs, voice output, or callable payloads", async () => {
    const violations: string[] = [];

    for (const path of await collectFiles(APP_ROOT, [".kt", ".xml"])) {
      const source = await readFile(path, "utf8");
      violations.push(
        ...matchingLocations(
          path,
          source,
          /Log\.[a-z]+\s*\([^)]*\bdni\b[^)]*\)/gis
        ),
        ...matchingLocations(
          path,
          source,
          /\.speak\s*\([^)]*\bdni\b[^)]*\)/gis
        )
      );

      const voiceStrings =
        /<string\s+name="(operator_voice[^"]*)"[^>]*>([\s\S]*?)<\/string>/gi;
      for (const match of source.matchAll(voiceStrings)) {
        if (!/\bdni\b/i.test(match[2])) continue;
        const line = source.slice(0, match.index ?? 0).split("\n").length;
        violations.push(`${path}:${line}`);
      }
    }

    for (const path of await collectFiles(FUNCTIONS_ROOT, [".ts"])) {
      const source = await readFile(path, "utf8");
      violations.push(
        ...matchingLocations(
          path,
          source,
          /console\.(?:log|info|warn|error|debug)\s*\([^)]*\bdni\b[^)]*\)/gis
        ),
        ...matchingLocations(
          path,
          source,
          /return\s*\{[\s\S]*?\};/g,
          (block) => /\bdni\b/i.test(block)
        )
      );
    }

    expect(violations).toEqual([]);
  });
});

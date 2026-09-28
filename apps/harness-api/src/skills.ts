import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

export interface SkillDescriptor {
  name: string;
  path: string;
  description: string;
}

const skillRoot = fileURLToPath(new URL("../../../packages/skills/", import.meta.url));
const builtInSkills = [
  "global/http-5xx/SKILL.md",
  "global/order-payment/SKILL.md",
  "templates/admin-app-dual/SKILL.md",
  "projects/demo-app/SKILL.md",
];

export async function loadSkillCatalog(): Promise<SkillDescriptor[]> {
  return Promise.all(builtInSkills.map(async (relative) => {
    const path = join(skillRoot, relative);
    const content = await readFile(path, "utf8");
    const name = content.match(/^name:\s*(.+)$/m)?.[1]?.trim() ?? relative;
    const description = content.match(/^description:\s*(.+)$/m)?.[1]?.trim() ?? "";
    return { name, path, description };
  }));
}

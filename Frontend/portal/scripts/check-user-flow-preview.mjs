import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import ts from "typescript";

// Exercise the real pure projection without adding another test runner dependency.
const path = fileURLToPath(new URL("../components/preview-runtime/scenario/productExperience.ts", import.meta.url));
const source = ts.transpileModule(readFileSync(path, "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const exports = {};
new Function("exports", source)(exports);
const { composeProductExperience, validateProductExperience } = exports;
const capability = (id, resourceName, type = "LIST") => ({
  id, resourceName, type, method: "GET", path: `/${resourceName}`, risk: "SAFE",
});
const scenario = (id, name, goal, capabilityId, status = "EXECUTABLE") => ({
  id, name, goal, actor: "사용자", status, entryStageId: "read", diagnostics: [],
  stages: [
    { id: "read", role: "DISCOVER", intent: name, capabilityId, nextStageIds: ["done"] },
    { id: "done", role: "COMPLETE", intent: "결과 확인", capabilityId: null, nextStageIds: [] },
  ],
});
const capabilities = [capability("rooms.list", "rooms"), capability("posts.list", "posts")];
const booking = scenario("book.room", "회의실 예약", "공간을 선택하고 예약", "rooms.list");
const community = scenario("write.post", "게시글 작성", "글을 작성하고 확인", "posts.list");
const graph = composeProductExperience([booking, community], capabilities);
assert.deepEqual(graph.screens.map((screen) => screen.label), ["회의실 예약", "게시글 작성"]);
assert.deepEqual(graph.screens[0].capabilityIds, ["rooms.list"]);
assert.deepEqual(graph.screens[1].capabilityIds, ["posts.list"]);
assert.equal(graph.screens.some((screen) => screen.kind === "PROFILE"), false);
assert.deepEqual(validateProductExperience(graph, [booking, community]), []);

const edited = composeProductExperience([booking], capabilities, [
  { id: "custom", title: "시간 선택 화면", capabilityIds: ["rooms.list"] },
]);
assert.equal(edited.screens[0].title, "시간 선택 화면");
assert.equal(edited.screens.some((screen) => screen.id === "resource-posts.list"), true);
const blocked = composeProductExperience([{ ...booking, status: "UNSUPPORTED" }], capabilities);
assert.equal(blocked.screens[0].actionIds.length, 0);
assert.equal(blocked.actions.length, 0);

// IDs must remain distinct even when slug normalization would collapse them.
const similar = composeProductExperience([
  { ...booking, id: "book.room" }, { ...booking, id: "book-room" },
], capabilities);
assert.equal(new Set(similar.actions.map((action) => action.id)).size, 2);
assert.deepEqual(validateProductExperience(similar, [
  { ...booking, id: "book.room" }, { ...booking, id: "book-room" },
]), []);
console.log("PASS user-goal screens, resource ownership, edited page plan, unsupported flow, stable IDs");

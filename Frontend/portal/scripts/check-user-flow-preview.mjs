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

// Re-run the real runtime with the compiled plan captured from live Commerce analysis.
function loadTs(name, imports = {}) {
  const code = ts.transpileModule(readFileSync(new URL(name, import.meta.url), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const result = {};
  new Function("exports", "require", code)(result, (id) => {
    if (!(id in imports)) throw Error(`Unexpected runtime dependency: ${id}`);
    return imports[id];
  });
  return result;
}
const realApi = loadTs("../components/preview-runtime/api.ts");
const runtime = loadTs("../components/preview-runtime/scenario/runtime.ts", { "../api": realApi });
const stage = (id, role, inputs = [], outputs = [], extras = {}) => ({
  id, role, intent: id, inputs, outputs, capabilityId: null, inputBindings: [], outputBindings: [], ...extras,
});
const chooseProduct = stage("choose-product", "SELECT", ["collection"], ["productId"]);
assert.deepEqual(runtime.resolveSelectionOutputs(chooseProduct, { selectedId: "product-42" }), { productId: "product-42" });
assert.throws(() => runtime.resolveSelectionOutputs(chooseProduct, { collection: [{ id: "product-1" }] }));
const selectCart = stage("select-cart", "SELECT", ["createdId"], ["cartId"]);
assert.deepEqual(runtime.resolveSelectionOutputs(selectCart, { selectedId: "product-42", createdId: "cart-99" }), { cartId: "cart-99" });
const executionStages = [
  stage("create-cart", "COMMIT", [], ["createdId"], { capabilityId: "carts.create", outputBindings: [{ to: "createdId" }] }),
  selectCart,
  stage("add-item", "COMMIT", ["cartId", "productId"], [], { capabilityId: "carts.items", inputBindings: [
    { target: "cartId", targetKind: "PATH", source: "$scenario.cartId", required: true },
    { target: "productId", targetKind: "BODY", source: "$scenario.productId", required: true },
  ] }),
];
assert.deepEqual(runtime.preflightScenarioExecution(executionStages, { productId: "product-42" }), []);
assert.notEqual(runtime.preflightScenarioExecution([selectCart], { selectedId: "product-42" }).length, 0);
assert.notEqual(runtime.preflightScenarioExecution([chooseProduct], { collection: [] }).length, 0);
assert.throws(() => runtime.resolveSelectionOutputs(stage("ambiguous", "SELECT", ["productId", "cartId"], ["id"]), { productId: "p", cartId: "c" }));
console.log("PASS real SELECT aliases, creation dependencies, no implicit first row, ambiguous input rejection");

assert.deepEqual(runtime.parseScenarioInput("[]"), []);
assert.deepEqual(runtime.parseScenarioInput('[{"productId":"p","quantity":2}]'), [{ productId: "p", quantity: 2 }]);
assert.deepEqual(runtime.parseScenarioInput('{"address":"Seoul"}'), { address: "Seoul" });
assert.equal(runtime.parseScenarioInput("1000"), 1000);
assert.equal(runtime.parseScenarioInput("KRW"), "KRW");
console.log("PASS shared product/inspector input conversion for arrays, objects, numbers and text");

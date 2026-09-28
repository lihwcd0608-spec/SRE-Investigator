import { useMemo, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { flowHasCycle, type AgentFlowEdge, type AgentFlowNode, type AgentFlowSpec } from "./agentSpec";
import { IconGitBranch, IconPlus, IconTrash, IconX } from "../icons";
import { mcpKindLabel } from "../mcp/connection";
import type { Binding, CatalogAsset, ToolKind } from "../types";

const NODE_W = 176;
const NODE_H = 92;

interface AgentFlowEditorProps {
  flow: AgentFlowSpec;
  onChange: (flow: AgentFlowSpec) => void;
  bindings: Binding[];
  skills: CatalogAsset[];
}

export function AgentFlowEditor({ flow, onChange, bindings, skills }: AgentFlowEditorProps) {
  const [selectedId, setSelectedId] = useState("");
  const [linkingFrom, setLinkingFrom] = useState("");
  const dragRef = useRef<{ id: string; dx: number; dy: number } | null>(null);
  const cyclic = flowHasCycle(flow);
  const selected = flow.nodes.find((node) => node.id === selectedId) ?? null;
  const size = useMemo(() => canvasSize(flow.nodes), [flow.nodes]);

  const patchFlow = (next: AgentFlowSpec) => {
    onChange(next);
  };

  const addNode = () => {
    const id = nextNodeId(flow.nodes);
    const index = flow.nodes.length;
    const node: AgentFlowNode = {
      id,
      title: `步骤 ${index + 1}`,
      instruction: "",
      toolKinds: [],
      skillSlugs: [],
      mcps: [],
      x: 24 + (index % 3) * 200,
      y: 24 + Math.floor(index / 3) * 120,
    };
    patchFlow({ nodes: [...flow.nodes, node], edges: flow.edges });
    setSelectedId(id);
    setLinkingFrom("");
  };

  const updateNode = (id: string, patch: Partial<AgentFlowNode>) => {
    patchFlow({
      nodes: flow.nodes.map((node) => (node.id === id ? { ...node, ...patch } : node)),
      edges: flow.edges,
    });
  };

  const removeNode = (id: string) => {
    patchFlow({
      nodes: flow.nodes.filter((node) => node.id !== id),
      edges: flow.edges.filter((edge) => edge.from !== id && edge.to !== id),
    });
    if (selectedId === id) setSelectedId("");
    if (linkingFrom === id) setLinkingFrom("");
  };

  const addEdge = (from: string, to: string) => {
    if (!from || !to || from === to) return;
    if (flow.edges.some((edge) => edge.from === from && edge.to === to)) return;
    patchFlow({ nodes: flow.nodes, edges: [...flow.edges, { from, to }] });
  };

  const removeEdge = (edge: AgentFlowEdge) => {
    patchFlow({
      nodes: flow.nodes,
      edges: flow.edges.filter((item) => item.from !== edge.from || item.to !== edge.to),
    });
  };

  const onNodePointerDown = (event: ReactPointerEvent<HTMLButtonElement>, node: AgentFlowNode) => {
    if (event.button !== 0) return;
    event.stopPropagation();
    if (linkingFrom) {
      if (linkingFrom !== node.id) addEdge(linkingFrom, node.id);
      setLinkingFrom("");
      setSelectedId(node.id);
      return;
    }
    setSelectedId(node.id);
    const canvas = event.currentTarget.closest(".flow-canvas");
    const rect = canvas?.getBoundingClientRect();
    dragRef.current = {
      id: node.id,
      dx: event.clientX - (rect?.left ?? 0) - node.x,
      dy: event.clientY - (rect?.top ?? 0) - node.y,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const onNodePointerMove = (event: ReactPointerEvent<HTMLButtonElement>) => {
    const drag = dragRef.current;
    if (!drag) return;
    const canvas = event.currentTarget.closest(".flow-canvas");
    const rect = canvas?.getBoundingClientRect();
    const x = Math.max(8, event.clientX - (rect?.left ?? 0) - drag.dx);
    const y = Math.max(8, event.clientY - (rect?.top ?? 0) - drag.dy);
    updateNode(drag.id, { x, y });
  };

  const onNodePointerUp = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (dragRef.current) {
      dragRef.current = null;
      if (event.currentTarget.hasPointerCapture(event.pointerId)) {
        event.currentTarget.releasePointerCapture(event.pointerId);
      }
    }
  };

  const toggleMcp = (node: AgentFlowNode, toolKind: ToolKind, profile: string) => {
    const exists = node.mcps.some((item) => item.toolKind === toolKind && item.profile === profile);
    const mcps = exists
      ? node.mcps.filter((item) => item.toolKind !== toolKind || item.profile !== profile)
      : [...node.mcps, { toolKind, profile }];
    updateNode(node.id, { mcps });
  };

  const toggleKind = (node: AgentFlowNode, kind: ToolKind) => {
    const toolKinds = node.toolKinds.includes(kind)
      ? node.toolKinds.filter((item) => item !== kind)
      : [...node.toolKinds, kind];
    updateNode(node.id, { toolKinds });
  };

  const toggleSkill = (node: AgentFlowNode, slug: string) => {
    const skillSlugs = node.skillSlugs.includes(slug)
      ? node.skillSlugs.filter((item) => item !== slug)
      : [...node.skillSlugs, slug];
    updateNode(node.id, { skillSlugs });
  };

  return (
    <div className="flow-editor">
      <div className="flow-editor-head">
        <div>
          <p className="kicker">Business flow</p>
          <h4>业务流程图</h4>
          <p className="hint">系统编排不变。图画的是一轮排查里的业务步骤：每步只打开该步 MCP 和 Skill。</p>
        </div>
        <div className="flow-editor-actions">
          <button className="btn secondary" type="button" onClick={addNode}>
            <IconPlus size={14} /> 添加步骤
          </button>
          <button
            className={`btn secondary${linkingFrom ? " flow-linking" : ""}`}
            type="button"
            disabled={flow.nodes.length < 2}
            onClick={() => setLinkingFrom(linkingFrom ? "" : (selectedId || flow.nodes[0]?.id || ""))}
          >
            <IconGitBranch size={14} /> {linkingFrom ? "点目标节点" : "连线"}
          </button>
        </div>
      </div>
      {cyclic ? <p className="error">流程图存在环，保存后运行会降级为单轮排查。请先删掉回边。</p> : null}
      {linkingFrom ? <p className="hint">已选起点，再点另一个节点完成连线。</p> : null}

      <div
        className="flow-canvas"
        style={{ minHeight: Math.max(280, size.height) }}
        onPointerDown={() => {
          setSelectedId("");
          setLinkingFrom("");
        }}
      >
        <svg className="flow-edges" width={size.width} height={size.height} aria-hidden="true">
          <defs>
            <marker id="flow-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="currentColor" />
            </marker>
          </defs>
          {flow.edges.map((edge) => {
            const from = flow.nodes.find((node) => node.id === edge.from);
            const to = flow.nodes.find((node) => node.id === edge.to);
            if (!from || !to) return null;
            return (
              <line
                key={`${edge.from}-${edge.to}`}
                x1={from.x + NODE_W / 2}
                y1={from.y + NODE_H / 2}
                x2={to.x + NODE_W / 2}
                y2={to.y + NODE_H / 2}
                markerEnd="url(#flow-arrow)"
              />
            );
          })}
        </svg>
        {flow.nodes.map((node, index) => (
          <button
            key={node.id}
            type="button"
            className={`flow-node${selectedId === node.id ? " selected" : ""}${linkingFrom === node.id ? " linking" : ""}`}
            style={{ left: node.x, top: node.y, width: NODE_W, minHeight: NODE_H }}
            onPointerDown={(event) => onNodePointerDown(event, node)}
            onPointerMove={onNodePointerMove}
            onPointerUp={onNodePointerUp}
            onPointerCancel={onNodePointerUp}
          >
            <span className="flow-node-index">{index + 1}</span>
            <strong>{node.title || "未命名"}</strong>
            <span className="flow-node-meta">{nodeSummary(node)}</span>
          </button>
        ))}
        {flow.nodes.length === 0 ? (
          <div className="flow-empty">
            <p>还没有步骤。不画节点时仍按现在的单轮排查。</p>
          </div>
        ) : null}
      </div>

      {flow.edges.length > 0 ? (
        <div className="flow-edge-list">
          {flow.edges.map((edge) => (
            <button key={`${edge.from}-${edge.to}`} type="button" className="tag" onClick={() => removeEdge(edge)}>
              {titleOf(flow, edge.from)} → {titleOf(flow, edge.to)}
              <IconX size={12} />
            </button>
          ))}
        </div>
      ) : null}

      {selected ? (
        <div className="flow-inspector">
          <div className="flow-inspector-head">
            <h4>步骤设置</h4>
            <button className="btn ghost" type="button" onClick={() => removeNode(selected.id)}>
              <IconTrash size={14} /> 删除
            </button>
          </div>
          <label>
            标题
            <input value={selected.title} onChange={(event) => updateNode(selected.id, { title: event.target.value })} placeholder="核订单" />
          </label>
          <label>
            本步指令
            <textarea
              value={selected.instruction}
              onChange={(event) => updateNode(selected.id, { instruction: event.target.value })}
              placeholder="按 orderId 查订单状态，只写本步证据"
            />
          </label>
          <fieldset className="check-list">
            <legend>本步 MCP</legend>
            {bindings.length === 0 ? <p className="hint">当前环境没有已启用的 MCP。</p> : null}
            {bindings.map((binding) => {
              const checked = selected.mcps.some((item) => item.toolKind === binding.toolKind && item.profile === binding.profile);
              return (
                <label className="check-item" key={binding.id}>
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => toggleMcp(selected, binding.toolKind, binding.profile)}
                  />
                  {mcpKindLabel(binding.toolKind)}
                  <span className="mono">{binding.profile}</span>
                </label>
              );
            })}
            <label className="check-item">
              <input type="checkbox" checked={selected.toolKinds.includes("CODE")} onChange={() => toggleKind(selected, "CODE")} />
              代码仓
              <span className="mono">CODE</span>
            </label>
          </fieldset>
          {skills.length > 0 ? (
            <fieldset className="check-list">
              <legend>本步 Skill</legend>
              {skills.map((skill) => (
                <label className="check-item" key={skill.id}>
                  <input
                    type="checkbox"
                    checked={selected.skillSlugs.includes(skill.slug)}
                    onChange={() => toggleSkill(selected, skill.slug)}
                  />
                  {skill.name}
                  <span className="mono">{skill.slug}</span>
                </label>
              ))}
            </fieldset>
          ) : (
            <p className="hint">项目里还没有已启用 Skill，步骤仍可只靠 MCP 执行。</p>
          )}
        </div>
      ) : (
        <p className="hint">点选一个步骤后，再配置该步 MCP、Skill 和短指令。</p>
      )}
    </div>
  );
}

function canvasSize(nodes: AgentFlowNode[]): { width: number; height: number } {
  let width = 520;
  let height = 280;
  for (const node of nodes) {
    width = Math.max(width, node.x + NODE_W + 32);
    height = Math.max(height, node.y + NODE_H + 32);
  }
  return { width, height };
}

function nextNodeId(nodes: AgentFlowNode[]): string {
  let index = nodes.length + 1;
  let id = `n${index}`;
  while (nodes.some((node) => node.id === id)) {
    index += 1;
    id = `n${index}`;
  }
  return id;
}

function titleOf(flow: AgentFlowSpec, id: string): string {
  return flow.nodes.find((node) => node.id === id)?.title || id;
}

function nodeSummary(node: AgentFlowNode): string {
  const parts: string[] = [];
  for (const mcp of node.mcps) {
    parts.push(mcp.profile);
  }
  if (node.toolKinds.includes("CODE") && !parts.includes("代码仓")) parts.push("代码仓");
  for (const slug of node.skillSlugs) parts.push(slug);
  return parts.length > 0 ? parts.join(" · ") : "未选 MCP / Skill";
}

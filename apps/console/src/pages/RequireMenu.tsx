import type { ReactNode } from "react";
import { useOutletContext } from "react-router-dom";
import type { LayoutContext } from "../layout/AppLayout";
import { hasMenu } from "../types";

export function RequireMenu({ code, children }: { code: string; children: ReactNode }) {
  const { me } = useOutletContext<LayoutContext>();
  if (!me) return null;
  if (!hasMenu(me, code)) {
    return (
      <div className="empty empty-hero">
        <h3>没有权限</h3>
        <p>当前账号没有该菜单权限，请联系管理员分配角色。</p>
      </div>
    );
  }
  return children;
}

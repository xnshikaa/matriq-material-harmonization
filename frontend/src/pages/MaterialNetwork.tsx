import React, { useEffect, useState, useMemo } from 'react';
import { api } from '../services/api';
import { MaterialGroup, MaterialRecord } from '../types';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Node,
  Edge,
  Position,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Layers, Network, RefreshCw, Info } from 'lucide-react';

export const MaterialNetwork: React.FC = () => {
  const [groups, setGroups] = useState<MaterialGroup[]>([]);
  const [records, setRecords] = useState<MaterialRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedGroup, setSelectedGroup] = useState<MaterialGroup | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const [g, r] = await Promise.all([
        api.getGroups(),
        api.getMaterials(),
      ]);
      setGroups(g);
      setRecords(r);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const recordsMap = useMemo(() => {
    const map: Record<string, MaterialRecord> = {};
    records.forEach((r) => { map[r.id] = r; });
    return map;
  }, [records]);

  // Build React Flow nodes and edges from DSU groups
  const { nodes, edges } = useMemo(() => {
    const flowNodes: Node[] = [];
    const flowEdges: Edge[] = [];

    // Filter to multi-member groups or top 10 groups for clean visualization
    const activeGroups = groups.filter((g) => g.memberIds.length >= 2).slice(0, 15);

    let groupCol = 0;
    let groupRow = 0;

    activeGroups.forEach((g, gIdx) => {
      const hubX = (gIdx % 3) * 380 + 150;
      const hubY = Math.floor(gIdx / 3) * 320 + 80;
      const hubId = `hub-${g.id}`;

      // Central National Code Hub Node
      flowNodes.push({
        id: hubId,
        position: { x: hubX, y: hubY },
        data: {
          label: (
            <div className="p-3 bg-[#172C48] text-white rounded border border-[#315BEA] shadow-md text-xs font-mono text-center">
              <div className="text-[10px] text-[#D6ED65] font-bold">NATIONAL CODE</div>
              <div className="font-black text-sm text-white mt-0.5">{g.nationalCode || 'PENDING NMC'}</div>
              <div className="text-[10px] text-[#9BB1CC] mt-1">{g.memberIds.length} Linked Members</div>
            </div>
          ),
        },
        type: 'default',
      });

      // Member nodes arranged in a circle around the hub
      const radius = 130;
      const angleStep = (2 * Math.PI) / g.memberIds.length;

      g.memberIds.forEach((memberId, mIdx) => {
        const rec = recordsMap[memberId];
        const angle = mIdx * angleStep;
        const nodeX = hubX + radius * Math.cos(angle);
        const nodeY = hubY + radius * Math.sin(angle);
        const nodeId = `rec-${memberId}`;

        const companyColor =
          rec?.companyName === 'CPCL'
            ? 'border-[#E77848] bg-[#FFF8F5]'
            : rec?.companyName === 'IOCL'
            ? 'border-[#315BEA] bg-[#F4F7FF]'
            : 'border-[#107C41] bg-[#F2F9F4]';

        flowNodes.push({
          id: nodeId,
          position: { x: nodeX, y: nodeY },
          data: {
            label: (
              <div className={`p-2 rounded border text-left text-[11px] shadow-sm max-w-[140px] ${companyColor}`}>
                <div className="font-bold text-[#172C48]">{rec ? rec.companyName : 'Entity'}</div>
                <div className="font-mono text-[#E77848] font-bold truncate">
                  {rec ? rec.originalMaterialCode : memberId}
                </div>
                <div className="text-[10px] text-[#566579] truncate mt-0.5">
                  {rec?.dimensions || rec?.technicalSpecification || ''}
                </div>
              </div>
            ),
          },
          type: 'default',
        });

        // Edge connecting member to NMC Hub
        flowEdges.push({
          id: `edge-${memberId}-${g.id}`,
          source: nodeId,
          target: hubId,
          animated: true,
          style: { stroke: '#315BEA', strokeWidth: 1.5 },
        });
      });
    });

    return { nodes: flowNodes, edges: flowEdges };
  }, [groups, recordsMap]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#DFE3E9] pb-4">
        <div>
          <h2 className="text-2xl font-black text-[#172C48] font-mono tracking-tight flex items-center space-x-2">
            <Network className="w-6 h-6 text-[#315BEA]" />
            <span>Material Equivalence Relationship Network</span>
          </h2>
          <p className="text-xs text-[#566579] mt-1">
            Visual graph representation of Disjoint Set Union (DSU) equivalence clusters connecting CPCL, IOCL, and ONGC catalog records into National Code hubs.
          </p>
        </div>

        <button
          onClick={loadData}
          className="inline-flex items-center space-x-1 px-3 py-1.5 bg-white border border-[#DFE3E9] text-[#172C48] rounded text-xs font-mono hover:bg-[#F2F0E9] cursor-pointer"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Refresh Graph</span>
        </button>
      </div>

      {/* Network Canvas */}
      <div className="bg-white border border-[#DFE3E9] rounded-sm p-2 shadow-sm">
        <div className="h-[600px] w-full bg-[#FAF9F5] rounded relative">
          {nodes.length > 0 ? (
            <ReactFlow
              nodes={nodes}
              edges={edges}
              fitView
              minZoom={0.2}
              maxZoom={1.5}
            >
              <Background color="#CBD2DC" gap={20} />
              <Controls />
              <MiniMap
                nodeColor={(n) => (n.id.startsWith('hub') ? '#172C48' : '#315BEA')}
                maskColor="rgba(242, 240, 233, 0.7)"
              />
            </ReactFlow>
          ) : (
            <div className="h-full flex flex-col items-center justify-center text-xs text-[#849DBE]">
              <Layers className="w-8 h-8 mb-2 text-[#CBD2DC]" />
              <p>No multi-member harmonization groups formed yet.</p>
              <p className="mt-1">Run the Harmonization Pipeline from the Command Center to form clusters.</p>
            </div>
          )}
        </div>
      </div>

      {/* Graph Legend */}
      <div className="bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm p-4 flex flex-wrap items-center justify-between gap-4 text-xs font-mono text-[#566579]">
        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-1.5">
            <span className="w-3 h-3 rounded-full bg-[#172C48] border border-[#315BEA]" />
            <span>National Material Code Hub (DSU Root)</span>
          </div>
          <div className="flex items-center space-x-1.5">
            <span className="w-3 h-3 rounded-full bg-[#FFF8F5] border border-[#E77848]" />
            <span>CPCL Material Record</span>
          </div>
          <div className="flex items-center space-x-1.5">
            <span className="w-3 h-3 rounded-full bg-[#F4F7FF] border border-[#315BEA]" />
            <span>IOCL Material Record</span>
          </div>
          <div className="flex items-center space-x-1.5">
            <span className="w-3 h-3 rounded-full bg-[#F2F9F4] border border-[#107C41]" />
            <span>ONGC Material Record</span>
          </div>
        </div>

        <div className="text-[11px] text-[#849DBE]">
          Total DSU Clusters: <strong>{groups.length}</strong>
        </div>
      </div>
    </div>
  );
};

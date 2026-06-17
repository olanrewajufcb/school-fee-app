import React, { useState, useCallback } from 'react';
import { ChevronRight, ChevronDown } from 'lucide-react';
import type { Schema, SchemaRef } from '@/types/openapi';
import { resolveSchema, resolveSchemaName, getSchemaProperties } from '@/utils/openapiParser';

interface SchemaRowProps {
  name: string;
  schema: Schema | SchemaRef;
  required: boolean;
  depth: number;
}

const SchemaRow: React.FC<SchemaRowProps> = ({ name, schema, required, depth }) => {
  const [expanded, setExpanded] = useState(false);
  const resolved = resolveSchema(schema);
  const typeName = resolveSchemaName(schema);
  const hasChildren = resolved && (resolved.properties || resolved.items || resolved.allOf);
  const isRef = '$ref' in schema && schema.$ref;

  const toggleExpanded = useCallback(() => {
    if (hasChildren) setExpanded((e) => !e);
  }, [hasChildren]);

  const indent = depth * 16;

  return (
    <>
      <tr
        className={`border-b border-slate-700/50 ${hasChildren ? 'cursor-pointer hover:bg-slate-800/50' : ''} transition-colors`}
        onClick={toggleExpanded}
      >
        <td className="py-2 px-3" style={{ paddingLeft: `${12 + indent}px` }}>
          <div className="flex items-center gap-1">
            {hasChildren && (
              <span className="text-slate-400">
                {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
              </span>
            )}
            <span className="font-mono text-sm text-cyan-400">{name}</span>
            {isRef && (
              <span className="text-xs text-slate-500 ml-1">(ref)</span>
            )}
          </div>
        </td>
        <td className="py-2 px-3">
          <span className="font-mono text-xs text-slate-400">{typeName}</span>
        </td>
        <td className="py-2 px-3">
          {required ? (
            <span className="text-xs text-red-400 font-medium">required</span>
          ) : (
            <span className="text-xs text-slate-600">optional</span>
          )}
        </td>
        <td className="py-2 px-3 text-sm text-slate-400 max-w-xs">
          {resolved?.description || '-'}
        </td>
      </tr>
      {expanded && resolved && (
        <>
          {resolved.properties &&
            getSchemaProperties(resolved).map((prop) => (
              <SchemaRow
                key={prop.name}
                name={prop.name}
                schema={prop.schema}
                required={prop.required}
                depth={depth + 1}
              />
            ))}
          {resolved.items && resolveSchema(resolved.items)?.properties &&
            getSchemaProperties(resolved.items).map((prop) => (
              <SchemaRow
                key={prop.name}
                name={prop.name}
                schema={prop.schema}
                required={prop.required}
                depth={depth + 1}
              />
            ))}
          {resolved.allOf?.map((subSchema, i) => {
            const sub = resolveSchema(subSchema);
            if (!sub?.properties) return null;
            return getSchemaProperties(sub).map((prop) => (
              <SchemaRow
                key={`${i}-${prop.name}`}
                name={prop.name}
                schema={prop.schema}
                required={prop.required}
                depth={depth + 1}
              />
            ));
          })}
        </>
      )}
    </>
  );
};

interface SchemaTableProps {
  schema: Schema | SchemaRef | undefined;
  className?: string;
}

const SchemaTable: React.FC<SchemaTableProps> = ({ schema, className = '' }) => {
  const properties = getSchemaProperties(schema);

  if (properties.length === 0) {
    const resolved = resolveSchema(schema);
    if (resolved?.type === 'array' && resolved.items) {
      return (
        <div className={`overflow-x-auto ${className}`}>
          <table className="w-full text-left">
            <thead>
              <tr className="bg-slate-800/50 border-b border-slate-700">
                <th className="py-2 px-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Property</th>
                <th className="py-2 px-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Type</th>
                <th className="py-2 px-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Required</th>
                <th className="py-2 px-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Description</th>
              </tr>
            </thead>
            <tbody>
              <SchemaRow name="[item]" schema={resolved.items} required={false} depth={0} />
            </tbody>
          </table>
        </div>
      );
    }
    return <p className="text-sm text-slate-500 italic">No schema properties defined</p>;
  }

  return (
    <div className={`overflow-x-auto ${className}`}>
      <table className="w-full text-left">
        <thead>
          <tr className="bg-slate-800/50 border-b border-slate-700">
            <th className="py-2 px-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Property</th>
            <th className="py-2 px-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Type</th>
            <th className="py-2 px-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Required</th>
            <th className="py-2 px-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Description</th>
          </tr>
        </thead>
        <tbody>
          {properties.map((prop) => (
            <SchemaRow
              key={prop.name}
              name={prop.name}
              schema={prop.schema}
              required={prop.required}
              depth={0}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default React.memo(SchemaTable);

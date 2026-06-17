import React, { useEffect, useRef } from 'react';
import type { Endpoint } from '@/types/openapi';
import { useStore } from '@/store/useStore';
import { generateExampleValue } from '@/utils/openapiParser';
import MethodBadge from './MethodBadge';
import EndpointPath from './EndpointPath';
import SchemaTable from './SchemaTable';
import JsonViewer from './JsonViewer';
import { Lock } from 'lucide-react';

interface DocContentProps {
  endpoints: Endpoint[];
}

const ERROR_CODES = [
  { code: 400, name: 'Bad Request', description: 'Invalid input data or missing required fields.' },
  { code: 401, name: 'Unauthorized', description: 'Authentication required. Bearer token missing or invalid.' },
  { code: 403, name: 'Forbidden', description: 'Insufficient permissions for this operation.' },
  { code: 404, name: 'Not Found', description: 'Resource not found (student, class, fee structure, etc.).' },
  { code: 409, name: 'Conflict', description: 'Resource already exists or state conflict.' },
  { code: 422, name: 'Unprocessable Entity', description: 'Validation error in request body.' },
  { code: 500, name: 'Internal Server Error', description: 'Unexpected server error.' },
];

const EndpointSection: React.FC<{ endpoint: Endpoint; onVisible: (id: string) => void }> = ({
  endpoint,
  onVisible,
}) => {
  const sectionRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          onVisible(endpoint.id);
        }
      },
      { rootMargin: '-20% 0px -60% 0px' }
    );

    if (sectionRef.current) {
      observer.observe(sectionRef.current);
    }

    return () => observer.disconnect();
  }, [endpoint.id, onVisible]);

  const op = endpoint.operation;
  const pathParams = op.parameters?.filter((p) => p.in === 'path') || [];
  const queryParams = op.parameters?.filter((p) => p.in === 'query') || [];
  const headerParams = op.parameters?.filter((p) => p.in === 'header') || [];
  const requestBodySchema = op.requestBody?.content?.['application/json']?.schema;
  const requestExample = requestBodySchema ? generateExampleValue(requestBodySchema) as Record<string, unknown> | null : null;

  return (
    <section
      ref={sectionRef}
      id={`endpoint-${endpoint.id}`}
      className="mb-10 pb-8 border-b border-slate-700/60 scroll-mt-4"
    >
      {/* Header */}
      <div className="mb-6">
        <div className="flex items-center gap-3 mb-2">
          <MethodBadge method={endpoint.method} size="lg" />
          <EndpointPath path={endpoint.path} size="lg" />
        </div>
        <p className="text-xs font-mono text-slate-500 mb-1">
          Operation: <span className="text-slate-400">{op.operationId}</span>
        </p>
        {op.description && (
          <p className="text-sm text-slate-400 mt-2">{op.description}</p>
        )}
        {op.tags && (
          <div className="flex gap-1.5 mt-2">
            {op.tags.map((tag) => (
              <span key={tag} className="text-[11px] font-mono text-slate-500 bg-slate-800 px-2 py-0.5 rounded">
                {tag}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Parameters */}
      {(pathParams.length > 0 || queryParams.length > 0 || headerParams.length > 0) && (
        <div className="mb-6">
          <h3 className="text-base font-semibold text-slate-100 mb-3">Parameters</h3>

          {pathParams.length > 0 && (
            <div className="mb-3">
              <h4 className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-2">Path</h4>
              <div className="bg-slate-800/30 rounded-md overflow-hidden border border-slate-700/50">
                <table className="w-full text-left">
                  <thead>
                    <tr className="bg-slate-800/50">
                      <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Name</th>
                      <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Type</th>
                      <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Required</th>
                      <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Description</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pathParams.map((p) => (
                      <tr key={p.name} className="border-t border-slate-700/30">
                        <td className="py-2 px-3 font-mono text-sm text-cyan-400">{p.name}</td>
                        <td className="py-2 px-3 font-mono text-xs text-slate-400">
                          {p.schema && 'type' in p.schema ? `${p.schema.type}${p.schema.format ? `(${p.schema.format})` : ''}` : 'string'}
                        </td>
                        <td className="py-2 px-3">
                          {p.required ? (
                            <span className="text-xs text-red-400">required</span>
                          ) : (
                            <span className="text-xs text-slate-600">optional</span>
                          )}
                        </td>
                        <td className="py-2 px-3 text-sm text-slate-400">{p.description || '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {queryParams.length > 0 && (
            <div className="mb-3">
              <h4 className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-2">Query</h4>
              <div className="bg-slate-800/30 rounded-md overflow-hidden border border-slate-700/50">
                <table className="w-full text-left">
                  <thead>
                    <tr className="bg-slate-800/50">
                      <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Name</th>
                      <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Type</th>
                      <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Required</th>
                      <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Description</th>
                    </tr>
                  </thead>
                  <tbody>
                    {queryParams.map((p) => (
                      <tr key={p.name} className="border-t border-slate-700/30">
                        <td className="py-2 px-3 font-mono text-sm text-cyan-400">{p.name}</td>
                        <td className="py-2 px-3 font-mono text-xs text-slate-400">
                          {p.schema && 'type' in p.schema ? `${p.schema.type}${p.schema.format ? `(${p.schema.format})` : ''}` : 'string'}
                        </td>
                        <td className="py-2 px-3">
                          {p.required ? (
                            <span className="text-xs text-red-400">required</span>
                          ) : (
                            <span className="text-xs text-slate-600">optional</span>
                          )}
                        </td>
                        <td className="py-2 px-3 text-sm text-slate-400">{p.description || '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {headerParams.length > 0 && (
            <div>
              <h4 className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-2">Headers</h4>
              <div className="bg-slate-800/30 rounded-md overflow-hidden border border-slate-700/50">
                <table className="w-full text-left">
                  <thead>
                    <tr className="bg-slate-800/50">
                      <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Name</th>
                      <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Type</th>
                      <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Required</th>
                      <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Description</th>
                    </tr>
                  </thead>
                  <tbody>
                    {headerParams.map((p) => (
                      <tr key={p.name} className="border-t border-slate-700/30">
                        <td className="py-2 px-3 font-mono text-sm text-cyan-400">{p.name}</td>
                        <td className="py-2 px-3 font-mono text-xs text-slate-400">
                          {p.schema && 'type' in p.schema ? p.schema.type : 'string'}
                        </td>
                        <td className="py-2 px-3">
                          {p.required ? (
                            <span className="text-xs text-red-400">required</span>
                          ) : (
                            <span className="text-xs text-slate-600">optional</span>
                          )}
                        </td>
                        <td className="py-2 px-3 text-sm text-slate-400">{p.description || '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Request Body */}
      {requestBodySchema && (
        <div className="mb-6">
          <h3 className="text-base font-semibold text-slate-100 mb-2">Request Body</h3>
          <span className="inline-block text-[11px] font-mono text-slate-500 bg-slate-800 px-2 py-1 rounded mb-3">
            Content-Type: application/json
          </span>
          <SchemaTable schema={requestBodySchema} className="mb-4" />
          {requestExample && (
            <div>
              <h4 className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-2">Example</h4>
              <JsonViewer data={requestExample} />
            </div>
          )}
        </div>
      )}

      {/* Responses */}
      <div className="mb-6">
        <h3 className="text-base font-semibold text-slate-100 mb-3">Responses</h3>
        {Object.entries(op.responses).map(([code, response]) => {
          const responseSchema = response.content?.['*/*']?.schema || response.content?.['application/json']?.schema;
          const responseExample = responseSchema ? generateExampleValue(responseSchema) as Record<string, unknown> | null : null;

          return (
            <div key={code} className="mb-4 bg-slate-800/30 rounded-lg border border-slate-700/50 overflow-hidden">
              <div className="flex items-center gap-3 px-4 py-3 bg-slate-800/50 border-b border-slate-700/50">
                <span className={`inline-flex items-center px-2.5 py-0.5 rounded text-xs font-mono font-bold ${
                  parseInt(code) >= 200 && parseInt(code) < 300
                    ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                    : parseInt(code) >= 400
                    ? 'bg-red-500/15 text-red-400 border border-red-500/30'
                    : 'bg-blue-500/15 text-blue-400 border border-blue-500/30'
                }`}>
                  {code}
                </span>
                <span className="text-sm text-slate-300">{response.description}</span>
              </div>
              <div className="p-4">
                {responseSchema && (
                  <>
                    <SchemaTable schema={responseSchema} className="mb-4" />
                    {responseExample && (
                      <div>
                        <h4 className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-2">Example</h4>
                        <JsonViewer data={responseExample} />
                      </div>
                    )}
                  </>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Error Codes Reference */}
      <div className="mb-6">
        <h3 className="text-base font-semibold text-slate-100 mb-3">Error Responses</h3>
        <div className="bg-slate-800/30 rounded-md overflow-hidden border border-slate-700/50">
          <table className="w-full text-left">
            <thead>
              <tr className="bg-slate-800/50">
                <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Code</th>
                <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Name</th>
                <th className="py-2 px-3 text-[11px] font-semibold text-slate-400 uppercase">Description</th>
              </tr>
            </thead>
            <tbody>
              {ERROR_CODES.map((err) => (
                <tr key={err.code} className="border-t border-slate-700/30">
                  <td className="py-2 px-3">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-mono font-bold ${
                      err.code >= 500
                        ? 'bg-red-500/15 text-red-400'
                        : err.code >= 400
                        ? 'bg-amber-500/15 text-amber-400'
                        : 'bg-emerald-500/15 text-emerald-400'
                    }`}>
                      {err.code}
                    </span>
                  </td>
                  <td className="py-2 px-3 text-sm font-medium text-slate-300">{err.name}</td>
                  <td className="py-2 px-3 text-sm text-slate-400">{err.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Authentication Note */}
      <div className="flex items-start gap-3 p-4 bg-blue-500/8 border-l-[3px] border-blue-500 rounded-r-md">
        <Lock size={16} className="text-blue-400 mt-0.5 flex-shrink-0" />
        <p className="text-sm text-slate-400">
          This endpoint requires authentication via Bearer token. Include{' '}
          <code className="text-xs font-mono text-blue-400 bg-blue-500/10 px-1 py-0.5 rounded">
            Authorization: Bearer &lt;token&gt;
          </code>{' '}
          in your request headers.
        </p>
      </div>
    </section>
  );
};

const DocContent: React.FC<DocContentProps> = ({ endpoints }) => {
  const { setActiveEndpoint, setActiveController } = useStore();
  const contentRef = useRef<HTMLDivElement>(null);

  const handleVisible = React.useCallback((id: string) => {
    const ep = endpoints.find((e) => e.id === id);
    if (ep) {
      setActiveEndpoint(id);
      setActiveController(ep.controller);
    }
  }, [endpoints, setActiveEndpoint, setActiveController]);

  return (
    <main
      ref={contentRef}
      className="flex-1 min-w-0 h-[calc(100vh-48px)] overflow-y-auto bg-slate-950 px-6 py-6 scrollbar-thin"
    >
      {/* API Info Header */}
      <div className="mb-8 pb-6 border-b border-slate-700">
        <h2 className="text-2xl font-bold text-slate-100 mb-2">School Fee Management API</h2>
        <p className="text-sm text-slate-400 mb-3">RESTful API for school fee management system</p>
        <div className="flex items-center gap-4 text-xs text-slate-500">
          <span>Version: <span className="text-slate-300">1.0.0</span></span>
          <span>Contact: <span className="text-slate-300">support@schoolfeeapp.com</span></span>
          <span>License: <span className="text-blue-400">MIT License</span></span>
        </div>
        <div className="mt-3 flex items-center gap-2">
          <span className="text-[11px] text-slate-500">Servers:</span>
          <span className="text-[11px] font-mono text-slate-400 bg-slate-800 px-2 py-1 rounded">http://localhost:8080</span>
          <span className="text-[11px] font-mono text-slate-400 bg-slate-800 px-2 py-1 rounded">http://localhost:8081</span>
        </div>
      </div>

      {/* Endpoints */}
      {endpoints.map((endpoint) => (
        <EndpointSection key={endpoint.id} endpoint={endpoint} onVisible={handleVisible} />
      ))}

      {/* Footer */}
      <footer className="mt-12 pt-6 border-t border-slate-700">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 text-xs text-slate-500">
          <div>
            <p>School Fee Management API</p>
            <p className="font-mono">Version 1.0.0</p>
          </div>
          <p>MIT License</p>
          <p>support@schoolfeeapp.com</p>
        </div>
        <p className="text-[11px] font-mono text-slate-600 mt-2">Generated from OpenAPI 3.1.0 specification</p>
      </footer>
    </main>
  );
};

export default DocContent;

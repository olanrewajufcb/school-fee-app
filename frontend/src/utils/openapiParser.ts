import type {
  OpenApiSpec,
  Endpoint,
  ControllerGroup,
  Schema,
  SchemaRef,
  Operation,
  PathMethods,
} from '@/types/openapi';
import openapiSpec from '@/data/openapi';

const CONTROLLER_ICONS: Record<string, string> = {
  'auth-controller': 'shield',
  'user-management-controller': 'users',
  'guardian-invitation-controller': 'mail',
  'school-controller': 'building-2',
  'class-controller': 'graduation-cap',
  'grade-level-controller': 'layers',
  'student-controller': 'users',
  'fee-controller': 'wallet',
  'payment-controller': 'credit-card',
  'receipt-controller': 'receipt',
  'result-controller': 'bar-chart-3',
  'report-controller': 'file-text',
  'notification-controller': 'bell',
  'paystack-webhook-controller': 'webhook',
};

const CONTROLLER_DISPLAY_NAMES: Record<string, string> = {
  'auth-controller': 'Authentication',
  'user-management-controller': 'User Management',
  'guardian-invitation-controller': 'Guardian Invitations',
  'school-controller': 'Schools',
  'class-controller': 'Classes',
  'grade-level-controller': 'Grade Levels',
  'student-controller': 'Students',
  'fee-controller': 'Fees',
  'payment-controller': 'Payments',
  'receipt-controller': 'Receipts',
  'result-controller': 'Results',
  'report-controller': 'Reports',
  'notification-controller': 'Notifications',
  'paystack-webhook-controller': 'Webhooks',
};

const METHOD_ORDER: Record<string, number> = {
  get: 0,
  post: 1,
  put: 2,
  patch: 3,
  delete: 4,
};

export function getSpec(): OpenApiSpec {
  return openapiSpec;
}

export function getServers(): { url: string; description: string }[] {
  return openapiSpec.servers || [];
}

export function parseEndpoints(): Endpoint[] {
  const endpoints: Endpoint[] = [];

  for (const [path, methods] of Object.entries(openapiSpec.paths)) {
    for (const [method, operation] of Object.entries(methods as PathMethods)) {
      if (!operation) continue;
      const op = operation as Operation;
      const controller = op.tags?.[0] || 'default';
      endpoints.push({
        id: op.operationId || `${method}-${path.replace(/[{}\/]/g, '-')}`.toLowerCase(),
        path,
        method: method.toLowerCase(),
        operation: op,
        controller,
      });
    }
  }

  endpoints.sort((a, b) => {
    if (a.controller !== b.controller) {
      return a.controller.localeCompare(b.controller);
    }
    const methodDiff = (METHOD_ORDER[a.method] ?? 99) - (METHOD_ORDER[b.method] ?? 99);
    if (methodDiff !== 0) return methodDiff;
    return a.path.localeCompare(b.path);
  });

  return endpoints;
}

export function groupByController(endpoints: Endpoint[]): ControllerGroup[] {
  const groups = new Map<string, ControllerGroup>();

  for (const ep of endpoints) {
    if (!groups.has(ep.controller)) {
      const displayName = CONTROLLER_DISPLAY_NAMES[ep.controller] || ep.controller;
      const icon = CONTROLLER_ICONS[ep.controller] || 'circle';
      groups.set(ep.controller, {
        name: ep.controller,
        displayName,
        icon,
        endpoints: [],
      });
    }
    groups.get(ep.controller)!.endpoints.push(ep);
  }

  return Array.from(groups.values());
}

export function getEndpointById(id: string, endpoints: Endpoint[]): Endpoint | undefined {
  return endpoints.find((ep) => ep.id === id);
}

export function resolveSchema(schema: Schema | SchemaRef | undefined): Schema | null {
  if (!schema) return null;
  if ('$ref' in schema && schema.$ref) {
    const refName = schema.$ref.replace('#/components/schemas/', '');
    const resolved = openapiSpec.components.schemas[refName];
    if (resolved) {
      return { ...resolved, $ref: schema.$ref } as Schema;
    }
    return null;
  }
  return schema as Schema;
}

export function resolveSchemaName(schema: Schema | SchemaRef | undefined): string {
  if (!schema) return 'unknown';
  if ('$ref' in schema && schema.$ref) {
    return schema.$ref.replace('#/components/schemas/', '');
  }
  const s = schema as Schema;
  if (s.type === 'array' && s.items) {
    return `array<${resolveSchemaName(s.items)}>`;
  }
  if (s.type) {
    return s.format ? `${s.type}(${s.format})` : s.type;
  }
  return 'object';
}

export function getSchemaProperties(schema: Schema | SchemaRef | undefined): Array<{
  name: string;
  schema: Schema | SchemaRef;
  required: boolean;
}> {
  const resolved = resolveSchema(schema);
  if (!resolved?.properties) return [];
  const required = resolved.required || [];
  return Object.entries(resolved.properties).map(([name, prop]) => ({
    name,
    schema: prop as Schema | SchemaRef,
    required: required.includes(name),
  }));
}

export function generateExampleValue(schema: Schema | SchemaRef | undefined): unknown {
  const resolved = resolveSchema(schema);
  if (!resolved) return null;

  if ('$ref' in resolved && resolved.$ref) {
    const refName = resolved.$ref.replace('#/components/schemas/', '');
    if (refName.startsWith('ApiResponse') || refName.startsWith('Page')) {
      return generateWrapperExample(resolved);
    }
  }

  if (resolved.type === 'object') {
    const result: Record<string, unknown> = {};
    if (resolved.properties) {
      for (const [key, prop] of Object.entries(resolved.properties)) {
        result[key] = generateExampleValue(prop as Schema | SchemaRef);
      }
    }
    if (resolved.additionalProperties && typeof resolved.additionalProperties === 'object') {
      result['additionalKey'] = generateExampleValue(resolved.additionalProperties);
    }
    return result;
  }

  if (resolved.type === 'array') {
    if (resolved.items) {
      return [generateExampleValue(resolved.items)];
    }
    return [];
  }

  if (resolved.type === 'string') {
    if (resolved.format === 'uuid') return '550e8400-e29b-41d4-a716-446655440000';
    if (resolved.format === 'date') return '2024-01-15';
    if (resolved.format === 'date-time') return '2024-01-15T10:30:00Z';
    if (resolved.format === 'email') return 'user@example.com';
    if (resolved.enum?.length) return resolved.enum[0];
    return 'string-value';
  }

  if (resolved.type === 'integer' || resolved.type === 'number') {
    if (resolved.format === 'int64') return 1000000;
    return 42;
  }

  if (resolved.type === 'boolean') return true;

  return null;
}

function generateWrapperExample(schema: Schema | SchemaRef): unknown {
  const resolved = resolveSchema(schema);
  if (!resolved?.properties) return {};

  const result: Record<string, unknown> = {};
  for (const [key, prop] of Object.entries(resolved.properties)) {
    if (key === 'data' && '$ref' in (prop as SchemaRef)) {
      const dataRef = (prop as SchemaRef).$ref?.replace('#/components/schemas/', '');
      if (dataRef) {
        const dataSchema = openapiSpec.components.schemas[dataRef];
        if (dataSchema) {
          result[key] = generateExampleValue(dataSchema);
          continue;
        }
      }
    }
    result[key] = generateExampleValue(prop as Schema | SchemaRef);
  }
  return result;
}

export function getAllEndpoints(): {
  endpoints: Endpoint[];
  controllers: ControllerGroup[];
} {
  const endpoints = parseEndpoints();
  const controllers = groupByController(endpoints);
  return { endpoints, controllers };
}

export { openapiSpec };

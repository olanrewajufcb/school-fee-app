export interface OpenApiSpec {
  openapi: string;
  info: ApiInfo;
  servers: Server[];
  paths: Record<string, PathMethods>;
  components: {
    schemas: Record<string, Schema>;
  };
}

export interface ApiInfo {
  title: string;
  description: string;
  contact: {
    name: string;
    email: string;
  };
  license: {
    name: string;
    url: string;
  };
  version: string;
}

export interface Server {
  url: string;
  description: string;
}

export interface PathMethods {
  get?: Operation;
  post?: Operation;
  put?: Operation;
  patch?: Operation;
  delete?: Operation;
}

export interface Operation {
  tags: string[];
  operationId: string;
  summary?: string;
  description?: string;
  parameters?: Parameter[];
  requestBody?: RequestBody;
  responses: Record<string, Response>;
}

export interface Parameter {
  name: string;
  in: 'path' | 'query' | 'header';
  required?: boolean;
  description?: string;
  schema: SchemaRef | Schema;
}

export interface RequestBody {
  description?: string;
  required?: boolean;
  content: Record<string, {
    schema: SchemaRef | Schema;
  }>;
}

export interface Response {
  description: string;
  content?: Record<string, {
    schema: SchemaRef | Schema;
  }>;
}

export interface SchemaRef {
  $ref: string;
}

export interface Schema {
  type?: string;
  format?: string;
  description?: string;
  nullable?: boolean;
  readOnly?: boolean;
  writeOnly?: boolean;
  enum?: (string | number | boolean)[];
  default?: unknown;
  example?: unknown;
  properties?: Record<string, Schema | SchemaRef>;
  items?: Schema | SchemaRef;
  additionalProperties?: boolean | Schema | SchemaRef;
  required?: string[];
  allOf?: (Schema | SchemaRef)[];
  oneOf?: (Schema | SchemaRef)[];
  anyOf?: (Schema | SchemaRef)[];
  $ref?: string;
}

export interface Endpoint {
  id: string;
  path: string;
  method: string;
  operation: Operation;
  controller: string;
}

export interface ControllerGroup {
  name: string;
  displayName: string;
  icon: string;
  endpoints: Endpoint[];
}

export interface QueuedRequest {
  id: string;
  timestamp: number;
  endpointId: string;
  method: string;
  path: string;
  serverUrl: string;
  headers: Record<string, string>;
  body: string;
  pathParams: Record<string, string>;
  queryParams: Record<string, string>;
  status: 'pending' | 'sent' | 'failed';
  response?: {
    status: number;
    statusText: string;
    headers: Record<string, string>;
    body: string;
    time: number;
  };
  error?: string;
}

export interface RequestHistoryItem {
  id: string;
  timestamp: number;
  endpointId: string;
  method: string;
  path: string;
  status: number;
  time: number;
}

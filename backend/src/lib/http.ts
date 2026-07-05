import { APIGatewayProxyEventV2, APIGatewayProxyStructuredResultV2, Context } from "aws-lambda";

/** An error carrying an HTTP status; thrown anywhere in a handler and mapped by [wrap]. */
export class HttpError extends Error {
  constructor(public statusCode: number, message: string) {
    super(message);
  }
}

const baseHeaders: Record<string, string> = {
  "content-type": "application/json",
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization,content-type",
  "access-control-allow-methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS",
};

export function json(statusCode: number, body: unknown): APIGatewayProxyStructuredResultV2 {
  return { statusCode, headers: baseHeaders, body: JSON.stringify(body) };
}

export const ok = (body: unknown) => json(200, body);
export const created = (body: unknown) => json(201, body);
export const noContent = (): APIGatewayProxyStructuredResultV2 => ({ statusCode: 204, headers: baseHeaders, body: "" });

/** Parses a JSON request body (handling base64-encoded API Gateway payloads). */
export function parseBody<T = Record<string, unknown>>(event: APIGatewayProxyEventV2): T {
  if (!event.body) return {} as T;
  const raw = event.isBase64Encoded ? Buffer.from(event.body, "base64").toString("utf8") : event.body;
  try {
    return JSON.parse(raw) as T;
  } catch {
    throw new HttpError(400, "Invalid JSON body");
  }
}

export const method = (event: APIGatewayProxyEventV2): string => event.requestContext.http.method.toUpperCase();

type Handler = (event: APIGatewayProxyEventV2) => Promise<APIGatewayProxyStructuredResultV2>;

/** Wraps a handler: disables the empty-event-loop wait, and maps thrown errors to JSON responses. */
export function wrap(fn: Handler) {
  return async (event: APIGatewayProxyEventV2, context: Context): Promise<APIGatewayProxyStructuredResultV2> => {
    context.callbackWaitsForEmptyEventLoop = false;
    try {
      return await fn(event);
    } catch (err) {
      if (err instanceof HttpError) return json(err.statusCode, { error: err.message });
      console.error("Unhandled error:", err);
      return json(500, { error: "Internal server error" });
    }
  };
}

export async function resolve(spec, ctx, next) {
  if ((spec.startsWith('./') || spec.startsWith('../')) && !/\.[a-z]+$/i.test(spec) && ctx.parentURL && ctx.parentURL.includes('material-color-utilities')) {
    try { return await next(spec + '.js', ctx); } catch (e) { return next(spec + '/index.js', ctx); }
  }
  return next(spec, ctx);
}

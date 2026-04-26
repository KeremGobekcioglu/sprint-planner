import { Controller, type Control, type FieldValues, type Path } from 'react-hook-form';

interface EpicInputProps<TFieldValues extends FieldValues> {
  control: Control<TFieldValues>;
  name: Path<TFieldValues>;
}

export function EpicInput<TFieldValues extends FieldValues>({ control, name }: EpicInputProps<TFieldValues>) {
  return (
    <Controller
      control={control}
      name={name}
      rules={{ required: true }}
      render={({ field }) => (
        <textarea
          className="h-40 w-full rounded-xl border border-slate-500/40 px-3 py-2 text-sm"
          placeholder="One epic per line, format: Title | Description"
          {...field}
        />
      )}
    />
  );
}

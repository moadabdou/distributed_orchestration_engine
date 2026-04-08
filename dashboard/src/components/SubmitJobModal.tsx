import React, { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { createJob } from '../api/jobs';
import { X, Play } from 'lucide-react';

interface SubmitJobModalProps {
  onClose: () => void;
}

const SubmitJobModal: React.FC<SubmitJobModalProps> = ({ onClose }) => {
  const queryClient = useQueryClient();
  const [payloadText, setPayloadText] = useState('{\n  "type": "Data Ingest",\n  "target": "example"\n}');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setError('');

    let parsed;
    try {
      parsed = JSON.parse(payloadText);
    } catch {
      setError('Invalid JSON payload');
      setIsSubmitting(false);
      return;
    }

    try {
      await createJob({ payload: JSON.stringify(parsed) });
      queryClient.invalidateQueries({ queryKey: ['jobsList'] });
      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to submit job');
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm transition-all duration-300">
      <div className="glass-panel w-full max-w-md p-6 relative animate-in fade-in zoom-in duration-300" style={{ background: 'rgba(255, 255, 255, 0.65)' }}>
        <button 
          onClick={onClose}
          className="absolute top-4 right-4 w-8 h-8 flex items-center justify-center rounded-full bg-white/50 text-slate-500 hover:text-slate-800 hover:bg-white transition-colors"
        >
          <X className="w-4 h-4" />
        </button>
        
        <h3 className="text-xl font-semibold text-slate-700 mb-1">Submit New Job</h3>
        <p className="text-xs text-slate-500 mb-4">Provide a valid JSON payload to orchestrate a new task in the network.</p>
        
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div>
            <textarea
              className="w-full h-40 bg-slate-800/80 text-mint-200 font-mono text-sm p-4 rounded-2xl outline-none focus:ring-2 focus:ring-purple-400 border border-slate-700/50 shadow-inner custom-scrollbar"
              value={payloadText}
              onChange={(e) => setPayloadText(e.target.value)}
              spellCheck={false}
            />
          </div>
          
          {error && <div className="text-red-500 text-xs font-semibold px-2">{error}</div>}
          
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full flex items-center justify-center gap-2 py-3 px-4 bg-gradient-to-tr from-purple-500 to-mint-500 text-white rounded-xl font-semibold hover:opacity-90 transition-opacity focus:ring-4 focus:ring-purple-200 disabled:opacity-50"
          >
            {isSubmitting ? 'Submitting...' : (
              <>
                <Play className="w-4 h-4" fill="currentColor" /> Submit Job
              </>
            )}
          </button>
        </form>
      </div>
    </div>
  );
};

export default SubmitJobModal;
